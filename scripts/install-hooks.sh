#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Installs a git pre-push hook that runs the fast CI subset (LLD §8): engine and generator unit tests plus the bank
# validator. The full gate set (lint, release builds, permission and size gates) stays in scripts/ci.sh and CI.
# Skip once with `git push --no-verify`.
set -euo pipefail
cd "$(dirname "$0")/.."
hook="$(git rev-parse --git-path hooks)/pre-push"
mkdir -p "$(dirname "$hook")"
cat > "$hook" <<'HOOK'
#!/usr/bin/env bash
# Installed by scripts/install-hooks.sh: fast pre-push subset of scripts/ci.sh.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
echo "pre-push: :engine:test :tools:generator:test :tools:generator:validateBank"
./gradlew --quiet :engine:test :tools:generator:test :tools:generator:validateBank
HOOK
chmod +x "$hook"
echo "Installed pre-push hook at $hook"
