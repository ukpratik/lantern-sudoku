#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Every CI gate (LLD §8). GitHub Actions runs exactly this script; run it locally before pushing.
set -euo pipefail
cd "$(dirname "$0")/.."

# Android SDK: ANDROID_HOME (CI), else local.properties (git-ignored), else the macOS default.
if [[ -z "${ANDROID_HOME:-}" && ! -f local.properties && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

GRADLE=(./gradlew --stacktrace)
step() { printf '\n==> %s\n' "$*"; }

step "1/6 Unit tests"
"${GRADLE[@]}" test

step "2/6 Bank validator (placeholder until M1)"
"${GRADLE[@]}" :tools:generator:validateBank

step "3/6 Android Lint, both flavours"
"${GRADLE[@]}" lintPlayDebug lintFdroidDebug

step "4/6 Release builds, both flavours (R8) + AboutLibraries licence gate"
"${GRADLE[@]}" assemblePlayRelease assembleFdroidRelease bundlePlayRelease

step "5/6 Permission gate: no permissions in either release APK"
scripts/check-permissions.sh \
  app/build/outputs/apk/play/release/app-play-release*.apk \
  app/build/outputs/apk/fdroid/release/app-fdroid-release*.apk

step "6/6 Size gate: Play download ≤ 10 MB (NFR-003)"
scripts/check-size.sh app/build/outputs/bundle/playRelease/app-play-release.aab 10000000

printf '\nAll gates passed.\n'
