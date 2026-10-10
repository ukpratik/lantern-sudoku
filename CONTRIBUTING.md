# Contributing to Lantern Sudoku

A few rules keep the app private, small and maintainable.

1. **No permissions, no network.** Nothing may add INTERNET or any other permission; `scripts/check-permissions.sh` fails the build.
2. **Licences.** Dependencies must be FOSS and GPL-3.0-compatible (Apache-2.0, MIT, BSD, OFL). The AboutLibraries licence gate fails the release build otherwise.
3. **Architecture.** Follow `product/05-architecture/LLD.md`: `:engine` stays pure Kotlin with no Android; significant decisions get an ADR in `product/05-architecture/adr/`.
4. **Tests.** New behaviour needs tests. Run `scripts/ci.sh` before pushing; it is exactly what CI runs.
5. **Source headers.** Start each new source file with `// SPDX-License-Identifier: GPL-3.0-or-later`.
6. **Commits.** Conventional commits (`feat:`, `fix:`, `build:`, `docs:` …), small and focused.
