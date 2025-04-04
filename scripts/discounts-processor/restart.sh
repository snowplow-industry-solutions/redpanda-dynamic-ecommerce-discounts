#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)
./down.sh
./up.sh
./logs.sh
