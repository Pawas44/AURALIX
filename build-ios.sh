#!/bin/bash
# VideoGuard Local iOS Build Script (Runs on macOS with Xcode)
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR/ios"

echo "==================================================="
echo "       VideoGuard - Local macOS iOS Build"
echo "==================================================="

echo "[*] Building Xcode project..."
xcodebuild -project VideoGuard.xcodeproj \
  -scheme VideoGuard \
  -configuration Release \
  -sdk iphoneos \
  -derivedDataPath ./DerivedData \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO \
  clean build

echo "[*] Locating built VideoGuard.app..."
APP_PATH=$(find ./DerivedData -name "VideoGuard.app" -type d | head -n 1)

if [ -z "$APP_PATH" ]; then
  echo "[ERROR] VideoGuard.app not found!"
  exit 1
fi

echo "[*] Packaging Payload into IPA..."
rm -rf Payload "$DIR/VideoGuard.ipa"
mkdir -p Payload
cp -R "$APP_PATH" Payload/
zip -r "$DIR/VideoGuard.ipa" Payload
rm -rf Payload

echo "==================================================="
echo "[SUCCESS] iOS IPA built: $DIR/VideoGuard.ipa"
echo "==================================================="

