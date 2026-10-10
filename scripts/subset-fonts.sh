#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Rebuilds app/src/main/res/font/*.ttf (design-system §3.1): static instances of the google/fonts variable fonts,
# Latin subset. Needs fonttools (pip install fonttools). Fonts are bundled; the app never downloads one.
# Usage: scripts/subset-fonts.sh <dir with Fraunces[SOFT,WONK,opsz,wght].ttf and Figtree[wght].ttf>
set -euo pipefail
SRC=${1:?usage: $0 <source dir>}
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# Basic Latin, Latin-1, and the punctuation the copy uses (– — ‘ ’ “ ” … • → › −).
U="U+0020-007E,U+00A0-00FF,U+2013,U+2014,U+2018,U+2019,U+201C,U+201D,U+2026,U+2022,U+2192,U+203A,U+2212"
for w in 500 600; do
  fonttools varLib.instancer "$SRC/Fraunces[SOFT,WONK,opsz,wght].ttf" wght=$w opsz=28 SOFT=0 WONK=1 -o "$TMP/fr$w.ttf" -q
  fonttools subset "$TMP/fr$w.ttf" --unicodes="$U" --layout-features='kern,liga,case' --name-IDs='*' --output-file="$OUT/fraunces_$w.ttf"
done
for w in 400 500 700; do
  fonttools varLib.instancer "$SRC/Figtree[wght].ttf" wght=$w -o "$TMP/fg$w.ttf" -q
  # Keep tnum: the timer, key counts and stats tables use tabular figures.
  fonttools subset "$TMP/fg$w.ttf" --unicodes="$U" --layout-features='kern,liga,tnum,case' --name-IDs='*' --output-file="$OUT/figtree_$w.ttf"
done
ls -l "$OUT"
