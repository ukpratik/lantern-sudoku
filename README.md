# Lantern Sudoku

**Calm, readable, offline sudoku for Android: no ads, no game over, honest difficulty, and hints that teach.**

Lantern Sudoku has no ads, no account, no tracking and **no network permission**: CI fails the build if any permission appears in a release APK. It is free software under GPL-3.0-or-later.

> **Status:** 1.0.0-rc1, the release candidate in QA (M0 to M5 done: engine and bank, playable board, teaching hints, daily and stats, themes, accessibility and hardening). Dev → QA handover: [`product/06-build/handover.md`](product/06-build/handover.md). Product, design and architecture docs: [`product/README.md`](product/README.md). Build plan: [`product/06-build/build-plan.md`](product/06-build/build-plan.md).

## Build, run, test

Requirements: JDK 17 or newer (CI uses 21), Android SDK with platform 37. Point Gradle at the SDK with `ANDROID_HOME` or a git-ignored `local.properties` (`sdk.dir=/Users/<you>/Library/Android/sdk`).

```bash
./gradlew assemblePlayDebug          # Google Play flavour (installs as io.github.ukpratik.lanternsudoku.debug)
./gradlew installFdroidDebug         # F-Droid flavour onto a connected device
./gradlew test                       # unit tests (engine, generator, app on Robolectric)
./gradlew :tools:generator:validateBank                  # validate every record of bank/v1 (also part of `check`)
./gradlew :tools:generator:run --args="report"           # grade distribution, hint replay, hand-check sample
scripts/install-hooks.sh                                 # optional pre-push hook: engine + generator tests, bank validator
scripts/ci.sh                        # every CI gate, exactly as GitHub Actions runs it
```

Debug builds have QA hooks (never in release): `adb shell am start --activity-single-top -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_load N-H-0123` starts that puzzle, add `--es dbg_board <81 chars>` to fill in a position's entries (for hint QA), and `--ez dbg_near_solved true` opens the current game with one empty cell left (also in the game's ⋮ menu). `--es dbg_date 2026-10-15` sets "today" (`clear` resets it) and `--ez dbg_daily true` opens today's daily. `--activity-single-top` lets the hooks reach a running app too; the full list is in the handover.

Startup (needs a device or emulator, API 33+): `./gradlew :app:generateBaselineProfile` regenerates the shipped Baseline Profile, and `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` measures cold start (add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR` on an emulator).

`scripts/ci.sh` runs: unit tests, the bank validator, Android Lint for both flavours (warnings are errors), R8 release builds of both flavours with the AboutLibraries licence gate, the permission gate (empty allowlist) and the size gate (Play download ≤ 10 MB).

Release builds are unsigned unless signing properties exist in `~/.gradle/gradle.properties` (`lantern.upload.*` for Play, `lantern.release.*` for GitHub APKs; see ADR-012). Keys and passwords never go in this repo.

## Project layout

```
engine/              pure Kotlin game engine (no Android): model, solver, techniques, hints, reducer
app/                 Android app (Jetpack Compose, Material 3); flavours play and fdroid
tools/generator/     JVM CLI that generates and validates the committed puzzle bank
bank/v1/             the committed puzzle bank (11,640 puzzles) and its manifest; see bank/v1/README.md
baselineprofile/     Baseline Profile generator and startup macrobenchmark (device only)
build-logic/         Gradle convention plugins (lantern.android.application, .compose, lantern.jvm.library)
scripts/             ci.sh, the permission and size gates, subset-fonts.sh
site/                home page and privacy policy (GitHub Pages at release)
product/             product docs: PRD, design, architecture (HLD, LLD, ADRs), build plan, QA
```

## Licence

GNU General Public License v3.0 or later. See [`LICENSE`](LICENSE).
