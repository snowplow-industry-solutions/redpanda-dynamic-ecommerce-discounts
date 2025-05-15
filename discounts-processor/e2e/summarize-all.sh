#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)
for f in data/*.jsonl; do
  ./summarize.sh $f
  version=kt ./summarize.sh $f
done
