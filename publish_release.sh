#!/bin/bash
set -e

# Usage: ./publish_release.sh <tag> "<release notes>"
# Example: ./publish_release.sh v2 "Added real-time feedback and bug fixes"

TAG=${1:-""}
NOTES=${2:-"New update with latest improvements and bug fixes"}

if [ -z "$TAG" ]; then
  echo "❌ Please provide a version tag, e.g.:"
  echo "   ./publish_release.sh v2 \"Added new feature\""
  exit 1
fi

echo "==> Building fresh APK..."
cd android
./gradlew assembleDebug
cd ..

APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
  echo "❌ APK build failed or file not found at $APK_PATH"
  exit 1
fi

echo "==> Creating GitHub release $TAG..."
gh release create "$TAG" "$APK_PATH" \
  --repo Madhav-Soni/Replock \
  --title "RepLock $TAG" \
  --notes "$NOTES"

echo "✅ Release $TAG successfully published to GitHub!"
echo "Your app on your phone will now prompt you to update!"

