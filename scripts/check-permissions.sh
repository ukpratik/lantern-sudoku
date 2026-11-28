#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Privacy gate (ADR-008): fails if a release APK's merged manifest requests any permission not explicitly allowed.
# Usage: scripts/check-permissions.sh [--allow <permission>]... <apk>...
# The fdroid APK is checked with no --allow: it requests no permissions at all. The play APK is checked with
# --allow com.android.vending.BILLING only (the optional tip jar; it lets the app talk to the Play Store, nothing else).
set -euo pipefail
ALLOWED=()
while [[ "${1:-}" == "--allow" ]]; do
  ALLOWED+=("$2")
  shift 2
done
# Added by AndroidX core to guard unexported receivers; it's a signature-level app-private permission, not a user one.
ALLOWED_SUFFIX=".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

if [[ $# -eq 0 ]]; then
  echo "usage: $0 <apk>..." >&2
  exit 2
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AAPT2=$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)
status=0
for apk in "$@"; do
  if [[ ! -f "$apk" ]]; then
    echo "FAIL: no such APK: $apk" >&2
    exit 2
  fi
  echo "Checking $apk"
  count=0
  while read -r perm; do
    [[ -z "$perm" ]] && continue
    count=$((count + 1))
    # "${ALLOWED[*]-}": macOS bash 3.2 with `set -u` errors on an empty array otherwise.
    if [[ " ${ALLOWED[*]-} " == *" $perm "* || "$perm" == *"$ALLOWED_SUFFIX" ]]; then
      echo "  ok    $perm"
    else
      echo "  BLOCK $perm"; status=1
    fi
  done < <("$AAPT2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")
  [[ $count -eq 0 ]] && echo "  (no permissions)"
done
exit $status
