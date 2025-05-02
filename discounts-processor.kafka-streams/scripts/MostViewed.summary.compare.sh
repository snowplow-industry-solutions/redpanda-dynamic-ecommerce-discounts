#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)/..

testName=${testName:-MostViewedSingleViewPerProduct}
f=$testName.summary.json
jsonl=e2e/data/${f/.summary/}l
expected=e2e/data/$f
generated=private/tmp/$f

./e2e/run.sh -t $testName -s &>/dev/null
./gradlew clean summarizeViews --args=$generated <$jsonl &>/dev/null

cat <<EOF
Showing diff between generated: $generated (created by ./gradlew ...)
                  and expected: $expected (created by ./e2e/run.sh ...)
EOF

[ "${1:-}" = "vim" ] &&
  vim -d <(jq . $generated) <(jq . $expected) ||
  diff <(jq . $generated) <(jq . $expected)
