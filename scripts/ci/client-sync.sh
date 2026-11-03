#!/bin/sh
# The API client is generated from contracts/openapi.yaml and never edited by hand.
set -eu

npm run --silent generate:api >/dev/null
if ! git diff --exit-code -- packages/api-client/src/schema.d.ts; then
  echo "client-sync: packages/api-client/src/schema.d.ts is out of date. Run npm run generate:api and commit it." >&2
  exit 1
fi
echo "client-sync: ok"
