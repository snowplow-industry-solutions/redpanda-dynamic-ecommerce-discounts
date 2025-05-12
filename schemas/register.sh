#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh

curl -X POST \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "apikey: $IGLU_SUPER_API_KEY" \
  --data-binary "@$SCHEMA_PATH" \
  "$IGLU_SERVER_URL/api/schemas"

echo
