import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Minimal lambda desugarer for the Gradle-less build: rewrites every LambdaMetafactory invokedynamic
 * into a generated class implementing the functional interface (Android has no LambdaMetafactory,
 * and the dx dexer does not desugar). Usage: java -cp asm.jar:asm-tree.jar Desugar.java IN_DIR OUT_DIR
 */
public class Desugar implements Opcodes {

    public static void main(String[] args) throws IOException {
        Path in = Paths.get(args[0]), out = Paths.get(args[1]);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(in)) { s.filter(p -> p.toString().endsWith(".class")).forEach(files::add); }
        int lambdas = 0;
        for (Path f : files) {
            ClassNode cn = new ClassNode();
            new ClassReader(Files.readAllBytes(f)).accept(cn, 0);
            List<ClassNode> generated = new ArrayList<>();
            lambdas += process(cn, generated);
            write(out, cn, true);
            for (ClassNode g : generated) write(out, g, false);
        }
        System.out.println("desugared " + lambdas + " lambdas in " + files.size() + " classes");
    }

    static void write(Path out, ClassNode cn, boolean keepFrames) throws IOException {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Path p = out.resolve(cn.name + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, cw.toByteArray());
    }

    static int process(ClassNode cn, List<ClassNode> generated) {
        int count = 0;
        List<MethodNode> accessors = new ArrayList<>();
        for (MethodNode m : new ArrayList<>(cn.methods)) {
            if (m.instructions == null) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof InvokeDynamicInsnNode)) continue;
                InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                if (!"java/lang/invoke/LambdaMetafactory".equals(indy.bsm.getOwner())) {
                    throw new IllegalStateException("unsupported invokedynamic in " + cn.name + "." + m.name + ": " + indy.bsm);
                }
                if (!"metafactory".equals(indy.bsm.getName())) {
                    throw new IllegalStateException("altMetafactory not supported in " + cn.name);
                }
                String lambdaName = cn.name + "$$Lambda$" + (generated.size());
                Handle impl = (Handle) indy.bsmArgs[1];
                impl = accessorFor(cn, impl, accessors);
                generated.add(lambdaClass(lambdaName, indy, (Type) indy.bsmArgs[0], impl, (Type) indy.bsmArgs[2]));
                m.instructions.set(indy, new MethodInsnNode(INVOKESTATIC, lambdaName, "create", indy.desc, false));
                count++;
            }
        }
        cn.methods.addAll(accessors);
        return count;
    }

    /** Private targets are reached through a package-private static accessor in the owner class. */
    static Handle accessorFor(ClassNode cn, Handle impl, List<MethodNode> accessors) {
        if (!impl.getOwner().equals(cn.name)) return impl;
        MethodNode target = null;
        for (MethodNode mn : cn.methods) if (mn.name.equals(impl.getName()) && mn.desc.equals(impl.getDesc())) target = mn;
        if (target == null || (target.access & ACC_PRIVATE) == 0) return impl;
        boolean isStatic = (target.access & ACC_STATIC) != 0;
        String name = "access$lambda$" + impl.getName().replace('$', '_') + "$" + accessors.size();
        Type[] a = Type.getArgumentTypes(impl.getDesc());
        Type ret = Type.getReturnType(impl.getDesc());
        StringBuilder d = new StringBuilder("(");
        if (!isStatic) d.append("L").append(cn.name).append(";");
        for (Type t : a) d.append(t.getDescriptor());
        d.append(")").append(ret.getDescriptor());
        MethodNode acc = new MethodNode(ACC_STATIC | ACC_SYNTHETIC, name, d.toString(), null, null);
        int slot = 0;
        if (!isStatic) { acc.visitVarInsn(ALOAD, 0); slot = 1; }
        for (Type t : a) { acc.visitVarInsn(t.getOpcode(ILOAD), slot); slot += t.getSize(); }
        if (isStatic) acc.visitMethodInsn(INVOKESTATIC, cn.name, impl.getName(), impl.getDesc(), false);
        else acc.visitMethodInsn(INVOKESPECIAL, cn.name, impl.getName(), impl.getDesc(), false);
        acc.visitInsn(ret.getOpcode(IRETURN));
        acc.visitMaxs(0, 0);
        accessors.add(acc);
        return new Handle(H_INVOKESTATIC, cn.name, name, d.toString(), false);
    }

    static ClassNode lambdaClass(String name, InvokeDynamicInsnNode indy, Type sam, Handle impl, Type inst) {
        Type itf = Type.getReturnType(indy.desc);
        Type[] captured = Type.getArgumentTypes(indy.desc);
        ClassNode c = new ClassNode();
        c.version = V1_6;
        c.access = ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC;
        c.name = name;
        c.superName = "java/lang/Object";
        c.interfaces.add(itf.getInternalName());
        for (int i = 0; i < captured.length; i++) c.visitField(ACC_PRIVATE | ACC_FINAL, "f" + i, captured[i].getDescriptor(), null, null);

        String ctorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, captured);
        MethodNode ctor = new MethodNode(0, "<init>", ctorDesc, null, null);
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        int slot = 1;
        for (int i = 0; i < captured.length; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(captured[i].getOpcode(ILOAD), slot);
            ctor.visitFieldInsn(PUTFIELD, name, "f" + i, captured[i].getDescriptor());
            slot += captured[i].getSize();
        }
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        c.methods.add(ctor);

        MethodNode create = new MethodNode(ACC_STATIC, "create", indy.desc, null, null);
        create.visitTypeInsn(NEW, name);
        create.visitInsn(DUP);
        slot = 0;
        for (Type t : captured) { create.visitVarInsn(t.getOpcode(ILOAD), slot); slot += t.getSize(); }
        create.visitMethodInsn(INVOKESPECIAL, name, "<init>", ctorDesc, false);
        create.visitInsn(ARETURN);
        create.visitMaxs(0, 0);
        c.methods.add(create);

        MethodNode m = new MethodNode(ACC_PUBLIC, indy.name, sam.getDescriptor(), null, null);
        int tag = impl.getTag();
        if (tag == H_NEWINVOKESPECIAL) {
            m.visitTypeInsn(NEW, impl.getOwner());
            m.visitInsn(DUP);
        }
        // target parameter list: receiver (for instance handles) + impl args
        List<Type> targets = new ArrayList<>();
        boolean instance = tag == H_INVOKEVIRTUAL || tag == H_INVOKEINTERFACE || tag == H_INVOKESPECIAL;
        if (instance) targets.add(Type.getObjectType(impl.getOwner()));
        for (Type t : Type.getArgumentTypes(impl.getDesc())) targets.add(t);
        Type[] samArgs = sam.getArgumentTypes();
        Type[] instArgs = inst.getArgumentTypes();
        if (targets.size() != captured.length + samArgs.length)
            throw new IllegalStateException("arity mismatch in " + name + " for " + impl);
        int k = 0;
        for (int i = 0; i < captured.length; i++, k++) {
            m.visitVarInsn(ALOAD, 0);
            m.visitFieldInsn(GETFIELD, name, "f" + i, captured[i].getDescriptor());
            adapt(m, captured[i], targets.get(k));
        }
        slot = 1;
        for (int i = 0; i < samArgs.length; i++, k++) {
            m.visitVarInsn(samArgs[i].getOpcode(ILOAD), slot);
            slot += samArgs[i].getSize();
            Type from = samArgs[i];
            // cast through the instantiated (more specific) type when the erased SAM type is Object-like
            if (from.getSort() == Type.OBJECT && instArgs[i].getSort() == Type.OBJECT && !from.equals(instArgs[i])) {
                m.visitTypeInsn(CHECKCAST, instArgs[i].getInternalName());
                from = instArgs[i];
            }
            adapt(m, from, targets.get(k));
        }
        Type implRet;
        switch (tag) {
            case H_INVOKESTATIC:
                m.visitMethodInsn(INVOKESTATIC, impl.getOwner(), impl.getName(), impl.getDesc(), impl.isInterface());
                implRet = Type.getReturnType(impl.getDesc());
                break;
            case H_INVOKEINTERFACE:
                m.visitMethodInsn(INVOKEINTERFACE, impl.getOwner(), impl.getName(), impl.getDesc(), true);
                implRet = Type.getReturnType(impl.getDesc());
                break;
            case H_INVOKEVIRTUAL:
            case H_INVOKESPECIAL: // non-private (private ones were redirected to accessors)
                m.visitMethodInsn(INVOKEVIRTUAL, impl.getOwner(), impl.getName(), impl.getDesc(), false);
                implRet = Type.getReturnType(impl.getDesc());
                break;
            case H_NEWINVOKESPECIAL:
                m.visitMethodInsn(INVOKESPECIAL, impl.getOwner(), "<init>", impl.getDesc(), false);
                implRet = Type.getObjectType(impl.getOwner());
                break;
            default:
                throw new IllegalStateException("handle kind " + tag);
        }
        Type samRet = sam.getReturnType();
        if (samRet.getSort() == Type.VOID) {
            if (implRet.getSize() == 1) m.visitInsn(POP);
            else if (implRet.getSize() == 2) m.visitInsn(POP2);
            m.visitInsn(RETURN);
        } else {
            adapt(m, implRet, samRet);
            m.visitInsn(samRet.getOpcode(IRETURN));
        }
        m.visitMaxs(0, 0);
        c.methods.add(m);
        return c;
    }

    /** Converts the value on top of the stack from one type to another (boxing/unboxing/casts/widening). */
    static void adapt(MethodNode m, Type from, Type to) {
        if (from.equals(to)) return;
        boolean fp = isPrim(from), tp = isPrim(to);
        if (fp && tp) {
            widen(m, from, to);
        } else if (fp) { // box
            Type box = boxOf(from);
            m.visitMethodInsn(INVOKESTATIC, box.getInternalName(), "valueOf", "(" + from.getDescriptor() + ")" + box.getDescriptor(), false);
            if (!to.equals(box) && !to.getInternalName().equals("java/lang/Object")) m.visitTypeInsn(CHECKCAST, to.getInternalName());
        } else if (tp) { // unbox
            Type box = boxOf(to);
            String owner = box.getInternalName();
            if (from.getInternalName().equals(owner)) {
                m.visitMethodInsn(INVOKEVIRTUAL, owner, to.getClassName() + "Value", "()" + to.getDescriptor(), false);
            } else if (to.getSort() == Type.BOOLEAN || to.getSort() == Type.CHAR) {
                m.visitTypeInsn(CHECKCAST, owner);
                m.visitMethodInsn(INVOKEVIRTUAL, owner, to.getClassName() + "Value", "()" + to.getDescriptor(), false);
            } else {
                m.visitTypeInsn(CHECKCAST, "java/lang/Number");
                m.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", to.getClassName() + "Value", "()" + to.getDescriptor(), false);
            }
        } else {
            if (!to.getInternalName().equals("java/lang/Object")) m.visitTypeInsn(CHECKCAST, to.getInternalName());
        }
    }

    static boolean isPrim(Type t) { return t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.DOUBLE; }

    static void widen(MethodNode m, Type from, Type to) {
        int f = from.getSort(), t = to.getSort();
        boolean fInt = f == Type.INT || f == Type.SHORT || f == Type.BYTE || f == Type.CHAR || f == Type.BOOLEAN;
        if (fInt && (t == Type.INT || t == Type.SHORT || t == Type.BYTE || t == Type.CHAR || t == Type.BOOLEAN)) return;
        if (fInt && t == Type.LONG) m.visitInsn(I2L);
        else if (fInt && t == Type.FLOAT) m.visitInsn(I2F);
        else if (fInt && t == Type.DOUBLE) m.visitInsn(I2D);
        else if (f == Type.LONG && t == Type.FLOAT) m.visitInsn(L2F);
        else if (f == Type.LONG && t == Type.DOUBLE) m.visitInsn(L2D);
        else if (f == Type.FLOAT && t == Type.DOUBLE) m.visitInsn(F2D);
        else throw new IllegalStateException("cannot convert " + from + " to " + to);
    }

    static Type boxOf(Type p) {
        switch (p.getSort()) {
            case Type.BOOLEAN: return Type.getType(Boolean.class);
            case Type.CHAR: return Type.getType(Character.class);
            case Type.BYTE: return Type.getType(Byte.class);
            case Type.SHORT: return Type.getType(Short.class);
            case Type.INT: return Type.getType(Integer.class);
            case Type.FLOAT: return Type.getType(Float.class);
            case Type.LONG: return Type.getType(Long.class);
            default: return Type.getType(Double.class);
        }
    }
}
