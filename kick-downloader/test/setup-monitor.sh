#!/usr/bin/env bash
# Builds androidx.test:monitor (needed by Robolectric) from the Apache-2.0 sources of android/android-test,
# for environments where Google's Maven repository is not reachable.
set -euo pipefail
cd "$(dirname "$0")"
W=$(mktemp -d)
git clone -q --depth 1 --filter=blob:none --sparse https://github.com/android/android-test "$W/at"
(cd "$W/at" && git sparse-checkout set runner/monitor/java/androidx/test espresso/idling_resource/java)
mkdir -p "$W/stubs/androidx/annotation" "$W/stubs/androidx/tracing" "$W/stubs/com/google/errorprone/annotations" "$W/out" libs
cat > "$W/stubs/androidx/annotation/RestrictTo.java" <<'J'
package androidx.annotation;
public @interface RestrictTo { Scope[] value(); enum Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, GROUP_ID, TESTS, SUBCLASSES } }
J
for a in NonNull Nullable; do echo "package androidx.annotation; public @interface $a {}" > "$W/stubs/androidx/annotation/$a.java"; done
echo "package androidx.annotation; public @interface VisibleForTesting { int otherwise() default 0; }" > "$W/stubs/androidx/annotation/VisibleForTesting.java"
for a in MustBeClosed CanIgnoreReturnValue; do echo "package com.google.errorprone.annotations; public @interface $a {}" > "$W/stubs/com/google/errorprone/annotations/$a.java"; done
echo 'package com.google.errorprone.annotations; public @interface InlineMe { String replacement(); String[] imports() default {}; String[] staticImports() default {}; }' > "$W/stubs/com/google/errorprone/annotations/InlineMe.java"
echo 'package androidx.tracing; public final class Trace { public static void beginSection(String s) {} public static void endSection() {} public static void forceEnableAppTracing() {} public static boolean isEnabled() { return false; } }' > "$W/stubs/androidx/tracing/Trace.java"
ALL=$(find ~/.gradle/caches/modules-2 -name "android-all-14-robolectric-10818077.jar" | head -1)
if [ -z "$ALL" ]; then
  ALL="$W/android-all.jar"
  curl -fsSL -o "$ALL" https://repo.maven.apache.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
fi
cd "$W/at"
EXC="MonitoringInstrumentation.java|FileTestStorage.java|hidden/ExposedInstrumentationApi.java|DefaultInterceptingActivityFactory.java|InstrumentationConnection.java|PlatformTestStorageRegistry.java"
javac -nowarn -encoding UTF-8 --release 17 -cp "$ALL" -d "$W/out" \
  $(find runner/monitor/java espresso/idling_resource/java -name '*.java' | grep -vE "$EXC") $(find "$W/stubs" -name '*.java')
cd - >/dev/null
jar cf libs/androidx-test-monitor.jar -C "$W/out" .
rm -rf "$W"
echo "built test/libs/androidx-test-monitor.jar"
