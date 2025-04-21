#!/usr/bin/env bash
set -eou pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <start> <end>"
  exit 1
fi

start=$(date -u -d "$1" +%s.%3N)
end=$(date -u -d "$2" +%s.%3N)
bc <<<"$end - $start"
