#!/bin/sh
# A handoff file is working state. It is deleted before the pull request is marked Ready.
set -eu

found=$(git ls-files 'docs/handoff/TB-*.md')
if [ -n "$found" ]; then
  echo "no-handoff: delete these files before marking the pull request Ready:" >&2
  echo "$found" >&2
  exit 1
fi
echo "no-handoff: ok"
