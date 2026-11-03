#!/bin/sh
# History is append-only: the appendix and source documents never change, a merged migration is
# never edited, renamed or deleted. New migrations are allowed.
# Usage: immutable-history.sh <base-commit>
set -eu

base="$1"
changed=$(git diff --name-status --no-renames "$base"...HEAD | awk '
  $2 ~ /^docs\/(appendix|source-docx)\// { print; next }
  $1 != "A" && $2 ~ /^backend\/src\/main\/resources\/db\/migration\// { print }
')

if [ -n "$changed" ]; then
  echo "immutable-history: these files must not change. Use a new migration or a CR instead:" >&2
  echo "$changed" >&2
  exit 1
fi
echo "immutable-history: ok"
