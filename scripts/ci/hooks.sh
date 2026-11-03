#!/bin/sh
# The pre-push hook only works if Git checks it out executable with LF line endings.
set -eu

mode=$(git ls-files -s .githooks/pre-push | cut -d' ' -f1)
eol=$(git ls-files --eol .githooks/pre-push | awk '{ print $1 }')
if [ "$mode" != "100755" ] || [ "$eol" != "i/lf" ]; then
  echo "hooks: .githooks/pre-push must be mode 100755 with LF endings, found $mode $eol" >&2
  exit 1
fi
echo "hooks: ok"
