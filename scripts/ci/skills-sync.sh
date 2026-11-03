#!/bin/sh
# Claude and Codex must load the same skills (docs/playbooks/README.md).
set -eu

if ! diff -r .claude/skills .agents/skills; then
  echo "skills-sync: .claude/skills and .agents/skills differ. Copy the change to both." >&2
  exit 1
fi
echo "skills-sync: ok"
