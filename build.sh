#!/usr/bin/env bash
# Builds a signed, zip-aligned APK without Gradle/Android Studio.
# Needs: JDK 17+, aapt, zipalign, apksigner, dx (Debian/Ubuntu: apt install aapt zipalign apksigner dalvik-exchange libandroid-23-java)
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/share/java/com.android.android-23.jar}
# javac compiles against a newer framework jar (for VibrationEffect etc.); resources still link against API 23
COMPILE_JAR=${COMPILE_JAR:-build/android-all-34.jar}
DX_JAR=${DX_JAR:-/usr/share/java/com.android.dx.jar}
SRC=app/src/main
OUT=build
APK=dist/led-remote.apk
KEYSTORE=${KEYSTORE:-keystore/release.keystore}

mkdir -p "$OUT"; rm -rf "$OUT/classes" "$OUT/gen" "$OUT/apk"; mkdir -p "$OUT/classes" "$OUT/gen" "$OUT/apk" dist

if [ ! -f "$COMPILE_JAR" ]; then
  mkdir -p "$(dirname "$COMPILE_JAR")"
  curl -fsSL -o "$COMPILE_JAR" https://repo.maven.apache.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
fi

# launcher icons
java tools/IconGen.java "$SRC/res" 2>/dev/null || true

aapt package -f -m -J "$OUT/gen" -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$ANDROID_JAR"
javac --release 8 -Xlint:-options -cp "$COMPILE_JAR" -d "$OUT/classes" $(find "$SRC/java" "$OUT/gen" -name '*.java')
java -cp "$DX_JAR" com.android.dx.command.Main --dex --min-sdk-version=21 --output="$OUT/apk/classes.dex" "$OUT/classes"
aapt package -f -M "$SRC/AndroidManifest.xml" -S "$SRC/res" -I "$ANDROID_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT/apk" && zip -q -j ../unsigned.apk classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias ledremote \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=LED Remote,O=LED Remote,C=AZ"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$APK" "$OUT/aligned.apk"
apksigner verify --verbose "$APK"
echo "APK: $APK"
