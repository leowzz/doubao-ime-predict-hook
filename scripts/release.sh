#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON:-python3}"
VERSION_TOOL="$ROOT/scripts/repo_version.py"
cd "$ROOT"

"$PYTHON_BIN" "$VERSION_TOOL" --root "$ROOT" check >/dev/null

dirty="$(git status --porcelain --untracked-files=normal)"
if [[ -n "$dirty" ]]; then
  echo "release: worktree must be clean:" >&2
  printf '%s\n' "$dirty" >&2
  exit 1
fi

if [[ -n "${V:-}" ]]; then
  NEW_VERSION="${V//$'\r'/}"
else
  NEW_VERSION="$("$PYTHON_BIN" "$VERSION_TOOL" --root "$ROOT" get --bump-patch)"
fi

"$PYTHON_BIN" "$VERSION_TOOL" --root "$ROOT" validate "$NEW_VERSION"
if git show-ref --verify --quiet "refs/tags/$NEW_VERSION"; then
  echo "release: git tag already exists: $NEW_VERSION" >&2
  exit 1
fi

"$PYTHON_BIN" "$VERSION_TOOL" --root "$ROOT" set "$NEW_VERSION"
"$PYTHON_BIN" "$VERSION_TOOL" --root "$ROOT" check "$NEW_VERSION"
git add -- gradle.properties
if ! git diff --cached --quiet -- gradle.properties; then
  git commit -m "chore: release $NEW_VERSION" -- gradle.properties
fi
git tag -a "$NEW_VERSION" -m "release $NEW_VERSION"
echo "release: version=$NEW_VERSION committed and tagged"
