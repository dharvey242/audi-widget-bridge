#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to your Android SDK directory}"
SDK_TOOLS="$ANDROID_SDK_ROOT/build-tools/${BUILD_TOOLS_VERSION:-36.1.0}"
PLATFORM="$ANDROID_SDK_ROOT/platforms/android-34/android.jar"
BUILD_DIR="${BUILD_DIR:-$PROJECT_DIR/build}"
export PATH="$JAVA_HOME/bin:$PATH"
for tool in aapt2 d8 zipalign apksigner; do
    [[ -x "$SDK_TOOLS/$tool" ]] || { echo "Missing SDK tool: $tool" >&2; exit 1; }
done
[[ -f "$PLATFORM" ]] || { echo 'Install Android SDK platform android-34.' >&2; exit 1; }
mkdir -p "$BUILD_DIR"
BUILD_DIR="$(cd -- "$BUILD_DIR" && pwd)"
TEMP_BUILD="$(mktemp -d "$BUILD_DIR/.compile.XXXXXX")"
trap 'rm -rf -- "$TEMP_BUILD"' EXIT
mkdir -p "$TEMP_BUILD/gen" "$TEMP_BUILD/classes" "$TEMP_BUILD/dex"
"$SDK_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" -o "$TEMP_BUILD/resources.zip"
"$SDK_TOOLS/aapt2" link -I "$PLATFORM" --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$TEMP_BUILD/gen" -o "$TEMP_BUILD/unsigned.apk" "$TEMP_BUILD/resources.zip"
sources=()
while IFS= read -r -d '' file; do sources+=("$file"); done < <(find "$PROJECT_DIR/src" "$TEMP_BUILD/gen" -name '*.java' -print0)
"$JAVA_HOME/bin/javac" --release 8 -classpath "$PLATFORM" -d "$TEMP_BUILD/classes" "${sources[@]}"
"$JAVA_HOME/bin/jar" cf "$TEMP_BUILD/classes.jar" -C "$TEMP_BUILD/classes" .
"$SDK_TOOLS/d8" --lib "$PLATFORM" --min-api 26 --output "$TEMP_BUILD/dex" "$TEMP_BUILD/classes.jar"
"$JAVA_HOME/bin/jar" uf "$TEMP_BUILD/unsigned.apk" -C "$TEMP_BUILD/dex" classes.dex
"$SDK_TOOLS/zipalign" -f 4 "$TEMP_BUILD/unsigned.apk" "$TEMP_BUILD/aligned.apk"
if [[ -n "${SIGNING_KEYSTORE:-}" ]]; then
    : "${SIGNING_ALIAS:?Set SIGNING_ALIAS for your own keystore}"
    : "${SIGNING_STORE_PASSWORD:?Set SIGNING_STORE_PASSWORD}"
    : "${SIGNING_KEY_PASSWORD:?Set SIGNING_KEY_PASSWORD}"
else
    SIGNING_KEYSTORE="$BUILD_DIR/debug.keystore"
    SIGNING_ALIAS=androiddebugkey
    export SIGNING_STORE_PASSWORD=android SIGNING_KEY_PASSWORD=android
    if [[ ! -f "$SIGNING_KEYSTORE" ]]; then
        (umask 077; "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$SIGNING_KEYSTORE" \
            -storepass:env SIGNING_STORE_PASSWORD -keypass:env SIGNING_KEY_PASSWORD \
            -alias "$SIGNING_ALIAS" -keyalg RSA -keysize 2048 -validity 3650 \
            -dname 'CN=Android Debug,O=Local Development,C=US' >/dev/null 2>&1)
    fi
    echo 'Using a local development key. Keep the same private key for compatible app updates.'
fi
export SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD
"$SDK_TOOLS/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_ALIAS" \
    --ks-pass env:SIGNING_STORE_PASSWORD --key-pass env:SIGNING_KEY_PASSWORD \
    --out "$BUILD_DIR/audi-widget-bridge.apk" "$TEMP_BUILD/aligned.apk"
"$SDK_TOOLS/apksigner" verify --verbose "$BUILD_DIR/audi-widget-bridge.apk"
"$SDK_TOOLS/zipalign" -c 4 "$BUILD_DIR/audi-widget-bridge.apk"
printf 'APK: %s\n' "$BUILD_DIR/audi-widget-bridge.apk"
