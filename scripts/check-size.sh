#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# NFR-003 size gate (ADR-008): the largest Play download must stay within the limit (10 MB for Lantern Sudoku).
# Usage: scripts/check-size.sh app/build/outputs/bundle/playRelease/app-play-release.aab [limit-bytes]
set -euo pipefail

AAB="${1:?path to .aab}"
LIMIT="${2:-10000000}"
BUNDLETOOL_VERSION="1.18.3"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

JAR="${BUNDLETOOL_JAR:-$HOME/.cache/bundletool/bundletool-all-$BUNDLETOOL_VERSION.jar}"
if [[ ! -f "$JAR" ]]; then
  mkdir -p "$(dirname "$JAR")"
  curl -sSfL -o "$JAR" \
    "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar"
fi

# Sizes don't depend on the signing key; a throwaway one keeps this independent of release secrets.
keytool -genkeypair -keystore "$WORK/size.jks" -storepass sizegate -keypass sizegate -alias size \
  -keyalg RSA -keysize 2048 -validity 1 -dname "CN=size" >/dev/null 2>&1

java -jar "$JAR" build-apks --bundle="$AAB" --output="$WORK/app.apks" \
  --ks="$WORK/size.jks" --ks-pass=pass:sizegate --ks-key-alias=size --key-pass=pass:sizegate >/dev/null

java -jar "$JAR" get-size total --apks="$WORK/app.apks" --dimensions=ABI | tee "$WORK/size.csv"

# CSV: ABI,MIN,MAX — the MAX download size for the worst ABI is what a user might download.
MAX=$(tail -n +2 "$WORK/size.csv" | tr -d '\r' | cut -d, -f3 | sort -n | tail -1)
echo "Largest download: $MAX bytes (limit $LIMIT)"
if (( MAX > LIMIT )); then
  echo "FAIL: download size over the NFR-003 budget" >&2
  exit 1
fi
echo "OK"
