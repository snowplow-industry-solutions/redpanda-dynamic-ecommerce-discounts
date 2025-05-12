#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

source ./common.sh

[ -d "node_modules" ] || npm i
node validate_events.js
