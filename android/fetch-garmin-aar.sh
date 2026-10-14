#!/usr/bin/env bash
# Fetch the Garmin Connect IQ Android SDK AAR at build time.
# The AAR is Garmin's binary — not redistributable, so it is never
# committed; CI and local builds fetch it from Garmin's GitHub.
set -euo pipefail
cd "$(dirname "$0")"

PINNED_SHA=728189b  # garmin/connectiq-android-sdk snapshot
URL="https://github.com/garmin/connectiq-android-sdk/archive/${PINNED_SHA}.zip"

if [ -f libs/connectiq-mobile-sdk.aar ]; then
    exit 0
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
curl -sL -o "$TMP/aar.zip" "$URL"
unzip -q -o "$TMP/aar.zip" -d "$TMP/unz"
AAR=$(find "$TMP/unz" -name "monkeybrains-sdk-release.aar" | head -1)
if [ -z "$AAR" ]; then
    echo "ERROR: AAR not found in ${URL}" >&2
    exit 1
fi
mkdir -p libs
cp "$AAR" libs/connectiq-mobile-sdk.aar
echo "fetched libs/connectiq-mobile-sdk.aar ($(du -h libs/connectiq-mobile-sdk.aar | cut -f1))"
