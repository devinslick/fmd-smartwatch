#!/usr/bin/env bash
# Manual build (no gradle): aapt2 + javac + d8 + zipalign + apksigner
# Requirements: JDK17 at ~/.local/share/jdk17, Android SDK at ~/.local/share/android-sdk
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_SDK:-$HOME/.local/share/android-sdk}
JAVA_HOME=${JAVA_HOME:-$HOME/.local/share/jdk17}
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
export PATH=$JAVA_HOME/bin:$PATH

rm -rf build && mkdir -p build/gen build/classes build/dex

# 1. Compile resources
$BT/aapt2 compile --dir res -o build/res.zip

# 2. Link: produces base APK + R.java
$BT/aapt2 link \
    -o build/base.apk \
    -I "$PLATFORM" \
    --manifest AndroidManifest.xml \
    --java build/gen \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 8 --version-name 0.6.0 \
    build/res.zip

# 2b. Compile AIDL (PebbleKit 2 companion binder)
# NOTE: one input per invocation - multi-input runs get misparsed as INPUT OUTPUT
mkdir -p build/aidl
$BT/aidl --lang=java -p $(dirname "$PLATFORM")/framework.aidl \
    -I src/aidl -o build/aidl \
    src/aidl/io/rebble/pebblekit2/common/SendDataCallback.aidl
$BT/aidl --lang=java -p $(dirname "$PLATFORM")/framework.aidl \
    -I src/aidl -o build/aidl \
    src/aidl/io/rebble/pebblekit2/common/UniversalRequestResponse.aidl

# 3. Compile Java (R.java + AIDL stubs + sources)
javac -source 1.8 -target 1.8 -Xlint:-options \
    -bootclasspath "$PLATFORM" \
    -classpath build/gen \
    -d build/classes \
    $(find build/gen build/aidl src -name "*.java")

# 4. Dex
$BT/d8 --release --lib "$PLATFORM" \
    --output build/dex \
    $(find build/classes -name "*.class")

# 5. Pack dex into APK
cp build/dex/classes.dex .
zip -q -j build/base.apk classes.dex
rm -f classes.dex

# 6. Align + sign
$BT/zipalign -f 4 build/base.apk build/aligned.apk
if [ ! -f debug.keystore ]; then
    keytool -genkeypair -v -keystore debug.keystore \
        -alias androiddebugkey -storepass android -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out FMDRing.apk build/aligned.apk
$BT/apksigner verify --verbose FMDRing.apk | head -5
ls -la FMDRing.apk
echo "OK: FMDRing.apk"
