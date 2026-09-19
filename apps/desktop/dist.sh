#!/usr/bin/env bash
# Build a private installer with embedded keys for a specific recipient.
# Usage: ./dist.sh <recipient> <platform> "vless://...#Name,vless://...#Name2"
#   recipient  — name used in output filename, e.g. "alice"
#   platform   — mac | windows | all
#   keys       — comma-separated vless:// URIs (quote the whole thing)
#
# Example:
#   ./dist.sh alice mac "vless://abc...#Home"
#   ./dist.sh bob windows "vless://abc...#Home,vless://xyz...#Backup"
set -euo pipefail

RECIPIENT="${1:-}"
PLATFORM="${2:-}"
KEYS="${3:-}"

if [[ -z "$RECIPIENT" || -z "$PLATFORM" || -z "$KEYS" ]]; then
    echo "Usage: $0 <recipient> <mac|windows|all> \"vless://...#Name\""
    exit 1
fi

build_mac() {
    make xray-mac
    ./gradlew :composeApp:packageDmg -PbakedKeys="$KEYS"
    local src
    src=$(ls composeApp/build/compose/binaries/main/dmg/Green-*.dmg | head -1)
    local out="dist/Green-${RECIPIENT}-arm64.dmg"
    mkdir -p dist
    cp "$src" "$out"
    echo "  → $out"
}

build_windows() {
    if [[ "$(uname)" != "MINGW"* && "$(uname)" != "CYGWIN"* && "$(uname)" != "Windows"* && "${OS:-}" != "Windows_NT" ]]; then
        echo "  ✗ MSI can only be built on Windows (jpackage limitation)."
        echo "    Use the GitHub Actions private build workflow instead:"
        echo "    https://github.com/0xCLWN/green-desktop/actions/workflows/private-build.yml"
        exit 1
    fi
    make xray-windows
    ./gradlew :composeApp:packageMsi -PbakedKeys="$KEYS"
    local src
    src=$(ls composeApp/build/compose/binaries/main/msi/Green-*.msi | head -1)
    local out="dist/Green-${RECIPIENT}-windows.msi"
    mkdir -p dist
    cp "$src" "$out"
    echo "  → $out"
}

echo "Building for: $RECIPIENT ($PLATFORM)"

case "$PLATFORM" in
    mac)     build_mac ;;
    windows) build_windows ;;
    all)     build_mac; build_windows ;;
    *)       echo "Unknown platform: $PLATFORM (use mac, windows, or all)"; exit 1 ;;
esac

echo "Done. Send the file(s) in dist/ to $RECIPIENT."
