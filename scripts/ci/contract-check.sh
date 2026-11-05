#!/bin/sh
# Breaking changes to the public API need /api/v2 or a compatible path (NFR15), so block them here.
# Usage: sh scripts/ci/contract-check.sh <base-ref>
# Uses a local oasdiff if one is on PATH, otherwise the pinned Docker image.
set -eu

base="${1:?usage: contract-check.sh <base-ref>}"
spec=contracts/openapi.yaml
image="tufin/oasdiff:v1.34.1@sha256:67a23cfea7d251e2ae34ee703ac8733c5d96bc2e7b90fd0f4d475ec99367b85f"

if ! git cat-file -e "$base:$spec" 2>/dev/null; then
  echo "contract-check: $spec does not exist at $base, nothing to compare"
  exit 0
fi
# Runs even when the spec is unchanged, so every PR proves the tool itself still works.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git show "$base:$spec" > "$tmp/base.yaml"
cp "$spec" "$tmp/head.yaml"

if command -v oasdiff >/dev/null 2>&1; then
  run() { oasdiff "$@" "$tmp/base.yaml" "$tmp/head.yaml"; }
else
  run() { docker run --rm -v "$tmp:/specs:ro" "$image" "$@" /specs/base.yaml /specs/head.yaml; }
fi

# Warnings are printed for the reviewer; only errors fail the check.
if ! run breaking --fail-on ERR; then
  echo "contract-check: breaking change in $spec. Keep it compatible or add a versioned path, see docs/playbooks/contract-change.md" >&2
  exit 1
fi
echo "contract-check: ok"
