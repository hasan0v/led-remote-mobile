import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Generates the launcher icons: dark tile with a ring of colour keys around a glowing white key. */
public class IconGen {
    public static void main(String[] a) throws Exception {
        String res = a[0];
        String[] dirs = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] px = {48, 72, 96, 144, 192};
        for (int i = 0; i < dirs.length; i++) {
            File d = new File(res, "mipmap-" + dirs[i]);
            d.mkdirs();
            ImageIO.write(draw(px[i] * 4), "png", new File(d, "ic_launcher.png"));
        }
    }

    static BufferedImage draw(int big) {
        BufferedImage img = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float s = big;
        g.setPaint(new GradientPaint(0, 0, new Color(0x1E2430), s, s, new Color(0x0B0E14)));
        g.fill(new RoundRectangle2D.Float(0, 0, s, s, s * 0.22f, s * 0.22f));
        Color[] cols = new Color[]{new Color(0xE05252), new Color(0xEE9B66), new Color(0xF7CE62),
                new Color(0x62AE70), new Color(0x53ADA3), new Color(0x4676BD), new Color(0x7560AE), new Color(0xDB95BF)};
        float c = s / 2, ring = s * 0.33f, dot = s * 0.085f;
        for (int i = 0; i < cols.length; i++) {
            double t = -Math.PI / 2 + i * 2 * Math.PI / cols.length;
            float x = (float) (c + Math.cos(t) * ring), y = (float) (c + Math.sin(t) * ring);
            g.setPaint(new RadialGradientPaint(x, y, dot * 1.9f, new float[]{0.4f, 1f},
                    new Color[]{new Color(cols[i].getRed(), cols[i].getGreen(), cols[i].getBlue(), 120), new Color(0, 0, 0, 0)}));
            g.fill(new Ellipse2D.Float(x - dot * 1.9f, y - dot * 1.9f, dot * 3.8f, dot * 3.8f));
            g.setPaint(new GradientPaint(x - dot, y - dot, cols[i].brighter(), x + dot, y + dot, cols[i].darker()));
            g.fill(new Ellipse2D.Float(x - dot, y - dot, dot * 2, dot * 2));
        }
        float r = s * 0.17f;
        g.setPaint(new RadialGradientPaint(c, c, r * 1.8f, new float[]{0.5f, 1f},
                new Color[]{new Color(255, 255, 255, 110), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Float(c - r * 1.8f, c - r * 1.8f, r * 3.6f, r * 3.6f));
        g.setPaint(new GradientPaint(c - r, c - r, Color.WHITE, c + r, c + r, new Color(0xC4C4C4)));
        g.fill(new Ellipse2D.Float(c - r, c - r, r * 2, r * 2));
        g.dispose();
        return img;
    }
}
