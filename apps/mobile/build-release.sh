#!/usr/bin/env bash
# Build and sign a release APK.
#
# Usage:
#   ./build-release.sh                         # generates a throw-away keystore
#   ./build-release.sh -k my.jks               # use existing keystore
#
# Environment variables (all optional):
#   KEYSTORE_FILE   path to .jks / .p12        default: ./green-release.jks (auto-generated)
#   KEY_ALIAS       key alias inside keystore   default: green
#   STORE_PASSWORD  keystore password           default: green123456
#   KEY_PASSWORD    key password                default: same as STORE_PASSWORD
#   OUTPUT_DIR      where to copy the APK       default: . (repo root)
#   ANDROID_HOME    Android SDK root            default: ~/Library/Android/sdk
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---- resolve tools ----
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -d "$ANDROID_HOME" ]; then
    ANDROID_HOME="$HOME/Android/Sdk"   # Linux default
fi

APKSIGNER="$(find "$ANDROID_HOME/build-tools" -name "apksigner" 2>/dev/null | sort -V | tail -1)"
if [ -z "$APKSIGNER" ]; then
    echo "error: apksigner not found under $ANDROID_HOME/build-tools" >&2
    exit 1
fi

# ---- keystore ----
KEYSTORE="${KEYSTORE_FILE:-$SCRIPT_DIR/green-release.jks}"
ALIAS="${KEY_ALIAS:-green}"
STORE_PASS="${STORE_PASSWORD:-green123456}"
KEY_PASS="${KEY_PASSWORD:-$STORE_PASS}"

if [ ! -f "$KEYSTORE" ]; then
    echo "[green] Generating keystore at $KEYSTORE"
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
        -dname "CN=Green VPN, O=Green, C=US" \
        -noprompt
fi

# ---- build ----
echo "[green] Building release APK…"
./gradlew assembleRelease

# ---- sign ----
UNSIGNED="$(find app/build/outputs/apk/release -name "*unsigned*.apk" | head -1)"
if [ -z "$UNSIGNED" ]; then
    echo "error: no unsigned APK found after build" >&2
    exit 1
fi

OUTPUT_DIR="${OUTPUT_DIR:-$SCRIPT_DIR}"
mkdir -p "$OUTPUT_DIR"
OUT="$OUTPUT_DIR/green-release.apk"

echo "[green] Signing → $OUT"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$STORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$OUT" \
    "$UNSIGNED"

echo "[green] Done:"
ls -lh "$OUT"
