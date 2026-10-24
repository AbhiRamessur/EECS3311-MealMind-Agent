#!/bin/sh
# Points Git at the repository's tracked hooks directory so the secret-scan pre-commit hook runs.
set -e

cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath scripts/hooks
chmod +x scripts/hooks/pre-commit
echo "Git hooks installed (core.hooksPath=scripts/hooks)."
