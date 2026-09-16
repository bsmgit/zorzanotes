#!/bin/bash

set -e

VERSION="1.0.0"

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK_DIR="/tmp/zorza-mac-build"
RELEASE_DIR="$PROJECT_ROOT/releases"

ICON="$PROJECT_ROOT/packaging/mac/Zorza.icns"
RESOURCE_DIR="$PROJECT_ROOT/packaging/mac/resources"

echo
echo "======================================"
echo " Zorza Notes macOS Release Build"
echo " Version $VERSION"
echo "======================================"
echo

cd "$PROJECT_ROOT"

# -------------------------------------------------------
# Verify required files
# -------------------------------------------------------

if [ ! -f "$ICON" ]; then
    echo "ERROR: Mac icon not found:"
    echo "$ICON"
    exit 1
fi

if [ ! -f "$RESOURCE_DIR/Zorza Notes-volume.icns" ]; then
    echo "ERROR: DMG volume icon not found:"
    echo "$RESOURCE_DIR/Zorza Notes-volume.icns"
    exit 1
fi

# -------------------------------------------------------
# Clean/build Java application
# -------------------------------------------------------

echo "1. Building Zorza Notes..."

mvn clean package

echo
echo "2. Removing macOS extended attributes..."

xattr -cr "$PROJECT_ROOT/target/package"

# -------------------------------------------------------
# Prepare temporary build directory
# -------------------------------------------------------

echo
echo "3. Preparing temporary build directory..."

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

# -------------------------------------------------------
# Prepare releases directory
# -------------------------------------------------------

echo
echo "4. Preparing releases directory..."

mkdir -p "$RELEASE_DIR"

# Remove only previous DMGs.
rm -f "$RELEASE_DIR"/*.dmg

# -------------------------------------------------------
# Build DMG
# -------------------------------------------------------

echo
echo "5. Building macOS DMG..."

jpackage \
  --type dmg \
  --name "Zorza Notes" \
  --app-version "$VERSION" \
  --description "Private local-first notes" \
  --vendor "Zorza" \
  --icon "$ICON" \
  --resource-dir "$RESOURCE_DIR" \
  --input "$PROJECT_ROOT/target/package" \
  --main-jar zorza-notes.jar \
  --main-class org.zorzanotes.ZorzaLauncher \
  --dest "$WORK_DIR" \
  --mac-package-identifier org.zorzanotes.app \
  --mac-package-name "Zorza Notes"

# -------------------------------------------------------
# Copy finished DMG
# -------------------------------------------------------

echo
echo "6. Copying finished DMG to releases..."

cp "$WORK_DIR"/*.dmg "$RELEASE_DIR/"

echo
echo "======================================"
echo " ZORZA NOTES MAC BUILD COMPLETE"
echo "======================================"
echo

ls -lh "$RELEASE_DIR"/*.dmg

echo
echo "Release directory:"
echo "$RELEASE_DIR"
echo