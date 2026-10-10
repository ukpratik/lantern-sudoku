# Lantern Sudoku

**Calm, readable, offline sudoku for Android: no ads, no game over, honest difficulty, and hints that teach.**

Lantern Sudoku has no ads, no account, no tracking and **no network permission**: CI fails the build if any permission appears in a release APK. It is free software under GPL-3.0-or-later.

> **Status:** in development (M0 foundation and M1 engine + puzzle bank are done). Product, design and architecture docs: [`product/README.md`](product/README.md). Build plan: [`product/06-build/build-plan.md`](product/06-build/build-plan.md).

## Build, run, test

Requirements: JDK 17 or newer (CI uses 21), Android SDK with platform 37. Point Gradle at the SDK with `ANDROID_HOME` or a git-ignored `local.properties` (`sdk.dir=/Users/<you>/Library/Android/sdk`).

```bash
./gradlew assemblePlayDebug          # Google Play flavour (installs as io.github.ukpratik.lanternsudoku.debug)
./gradlew installFdroidDebug         # F-Droid flavour onto a connected device
./gradlew test                       # unit tests (engine, generator)
./gradlew :tools:generator:validateBank                  # validate every record of bank/v1 (also part of `check`)
./gradlew :tools:generator:run --args="report"           # grade distribution, hint replay, hand-check sample
scripts/install-hooks.sh                                 # optional pre-push hook: engine + generator tests, bank validator
scripts/ci.sh                        # every CI gate, exactly as GitHub Actions runs it
```

`scripts/ci.sh` runs: unit tests, the bank validator, Android Lint for both flavours (warnings are errors), R8 release builds of both flavours with the AboutLibraries licence gate, the permission gate (empty allowlist) and the size gate (Play download ≤ 10 MB).

Release builds are unsigned unless signing properties exist in `~/.gradle/gradle.properties` (`lantern.upload.*` for Play, `lantern.release.*` for GitHub APKs; see ADR-012). Keys and passwords never go in this repo.

## Project layout

```
engine/              pure Kotlin game engine (no Android): model, solver, techniques, hints, reducer
app/                 Android app (Jetpack Compose, Material 3); flavours play and fdroid
tools/generator/     JVM CLI that generates and validates the committed puzzle bank
bank/v1/             the committed puzzle bank (11,640 puzzles) and its manifest; see bank/v1/README.md
build-logic/         Gradle convention plugins (lantern.android.application, .compose, lantern.jvm.library)
scripts/             ci.sh and the permission and size gates
product/             product docs: PRD, design, architecture (HLD, LLD, ADRs), build plan, QA
```

## Licence

GNU General Public License v3.0 or later. See [`LICENSE`](LICENSE).
