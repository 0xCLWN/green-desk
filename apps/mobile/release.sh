#!/usr/bin/env bash
set -euo pipefail

GRADLE="app/build.gradle.kts"

current_name=$(grep 'versionName' "$GRADLE" | grep -o '".*"' | tr -d '"')
current_code=$(grep 'versionCode' "$GRADLE" | grep -o '[0-9]\+')

echo "Current version: $current_name (code $current_code)"
echo -n "New version: "
read -r new_name

new_code=$((current_code + 1))

sed -i '' "s/versionCode = $current_code/versionCode = $new_code/" "$GRADLE"
sed -i '' "s/versionName = \"$current_name\"/versionName = \"$new_name\"/" "$GRADLE"

echo "Updated: $current_name → $new_name (code $current_code → $new_code)"

git add "$GRADLE"
git commit -m "chore/ bump version to $new_name"
git tag "v$new_name"

echo ""
echo "Ready to push. Run:"
echo "  git push origin main && git push origin v$new_name"
