#!/usr/bin/env bash
set -euo pipefail

GRADLE="composeApp/build.gradle.kts"

current=$(grep 'val appVersion' "$GRADLE" | grep -o '"[^"]*"' | tr -d '"')

echo "Current version: $current"
printf "New version: "
read -r new_version

echo "Checking compilation..."
./gradlew :composeApp:compileKotlinDesktop -q
echo "Compilation OK"

sed -i '' "s/val appVersion = \"$current\"/val appVersion = \"$new_version\"/" "$GRADLE"

echo "Updated: $current → $new_version"

git add "$GRADLE"
git commit -m "chore: bump version to $new_version"
git tag "v$new_version"

echo ""
echo "Run to publish:"
echo "  git push origin main && git push origin v$new_version"
