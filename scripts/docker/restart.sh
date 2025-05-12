#!/usr/bin/env bash
set -eou pipefail
BASE_DIR=$(dirname $(command -v $0))

cd $BASE_DIR
./down.sh "$@"
./clean.sh
./build.sh "$@"
./up.sh "$@"
