#!/usr/bin/env bash
# Builds a signed, zip-aligned APK without Gradle / Android Studio (no third-party libraries).
# Needs: JDK 17+, aapt2, zipalign, apksigner, dx (+ ASM, downloaded from Maven Central)
#   Debian/Ubuntu: apt install android-sdk-build-tools zipalign apksigner dalvik-exchange
set -euo pipefail
cd "$(dirname "$0")"

BT=${BT:-/usr/lib/android-sdk/build-tools/29.0.3}
JARS=${JARS:-$HOME/android-sdk/platforms}
# Resources are linked against API 32 (aapt2 29 cannot read the sparse resource table of newer jars);
# Java is compiled against the API 35 framework.
RES_JAR=${RES_JAR:-$JARS/android-32/android.jar}
COMPILE_JAR=${COMPILE_JAR:-$JARS/android-35/android.jar}
DX_JAR=${DX_JAR:-/usr/share/java/com.android.dx.jar}
SRC=app/src/main
OUT=build
APK=dist/KickDownloader.apk
KEYSTORE=${KEYSTORE:-keystore/release.keystore}

# ASM (for tools/Desugar.java: Android has no LambdaMetafactory and dx does not desugar lambdas)
TOOLS=${TOOLS:-$HOME/.cache/kickdl-build}
mkdir -p "$TOOLS"
for a in asm asm-tree; do
  if [ ! -f "$TOOLS/$a-9.7.1.jar" ]; then
    curl -fsSL --retry 5 --retry-delay 5 -o "$TOOLS/$a-9.7.1.jar" "https://repo.maven.apache.org/maven2/org/ow2/asm/$a/9.7.1/$a-9.7.1.jar"
  fi
done

for api in 32 35; do
  jar="$JARS/android-$api/android.jar"
  if [ ! -f "$jar" ]; then
    mkdir -p "$(dirname "$jar")"
    curl -fsSL -o "$jar" "https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-$api/android.jar"
  fi
done

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/desugared" "$OUT/gen" "$OUT/apk" dist

"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BT/aapt2" link -I "$RES_JAR" --manifest "$SRC/AndroidManifest.xml" -o "$OUT/unsigned.apk" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 34 --version-code 4 --version-name 1.0.3 \
  --auto-add-overlay "$OUT/res.zip"

javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -cp "$COMPILE_JAR" -d "$OUT/classes" \
  $(find "$SRC/java" "$OUT/gen" -name '*.java')
java -cp "$TOOLS/asm-9.7.1.jar:$TOOLS/asm-tree-9.7.1.jar" tools/Desugar.java "$OUT/classes" "$OUT/desugared"
if grep -rlq "LambdaMetafactory" "$OUT/desugared"; then echo "lambda desugaring failed" >&2; exit 1; fi
java -cp "$DX_JAR" com.android.dx.command.Main --dex --min-sdk-version=26 --output="$OUT/apk/classes.dex" "$OUT/desugared"
if grep -q "LambdaMetafactory" "$OUT/apk/classes.dex"; then echo "dex still references LambdaMetafactory" >&2; exit 1; fi
(cd "$OUT/apk" && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias kickdl \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Kick Downloader,O=KickDL,C=AZ"
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify "$APK"
ls -la "$APK"
