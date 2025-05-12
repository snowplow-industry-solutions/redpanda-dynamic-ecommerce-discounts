#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh
check_usage "$@"
behaviour=$1

[ -d node_modules ] || npm install

cleanup() {
  pkill -P $$
  exit 0
}

trap cleanup SIGINT SIGTERM

behaviour_file=tests/${behaviour}.spec.ts
[ -f $behaviour_file ] || { echo "File not found: $behaviour_file" && exit 1; }

mkdir -p $DATA_DIR
exec > >(tee "$DATA_DIR/${behaviour}.log") 2>&1
npx playwright test $behaviour_file
