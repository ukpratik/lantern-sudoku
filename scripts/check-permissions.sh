#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Privacy gate (ADR-008): fails if a release APK's merged manifest requests ANY permission.
# Usage: scripts/check-permissions.sh <apk>...
set -euo pipefail
# Empty on purpose: Lantern Sudoku requests no permissions at all.
ALLOWED=()
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
