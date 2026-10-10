# Puzzle bank v1

Committed source (ADR-004). Do not edit by hand: CI fails on any change to a frozen record.

| File | Records | Pool, level |
|---|---|---|
| `newgame-easy.txt` / `-medium` / `-hard` / `-expert` | 2,000 each | New game (US-002) |
| `daily-easy.txt` / `-medium` / `-hard` | 1,040 each (520 weeks × 2 weekdays) | Daily (US-014, ADR-005) |
| `daily-expert.txt` | 520 (Sundays) | Daily |

- **Record v1:** exactly 84 bytes = 81 cells (`1`–`9`, `.` empty) + space + hardest-technique code + LF. No header; record `i` is at byte `84 × i`.
- **Codes:** `a` Full house, `b` Hidden single, `c` Naked single, `d` Pointing, `e` Box-line reduction, `f` Naked pair, `g` Hidden pair, `h` Naked triple, `i` Hidden triple, `j` X-Wing. The level is the code's tier.
- **Puzzle ID:** `<N|D>-<E|M|H|X>-<index, 4 digits>`, for example `N-H-0123` = line 124 of `newgame-hard.txt`. Permanent.
- **`manifest.json`:** counts and frozen prefixes (release, count, SHA-256 of the first `count × 84` bytes). Append-only.

## Commands (from the repo root)

```bash
./gradlew :tools:generator:validateBank                          # what CI runs (about 1 s on 14 cores)
./gradlew :tools:generator:run --args="report"                   # distribution, hint replay, product/06-build/m1-hand-check.md
./gradlew :tools:generator:run --args="generate --newgame 3000"  # APPEND New-game records up to 3,000 per level
./gradlew :tools:generator:run --args="freeze bank/v1 1.0.0"     # record frozen prefixes at a release
```

`generate` only appends: record `i` comes from its own SplitMix64 seed (`GenConfig` v1), so extending a file never changes existing records, and a regeneration from scratch is byte-identical whatever the thread count. Generating all 11,640 records took 48 s on 14 cores (M1).
