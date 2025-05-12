#!/usr/bin/env bash

BASE_DIR=$(pwd)
DATA_DIR=$BASE_DIR/data

check_usage() {
  local tests=$(
    find tests -type f -name '*.spec.ts' |
      sed 's/tests\///g;s/\.spec\.ts//g'
  )
  if [ $# -lt 1 ]; then
    echo Usage: $0 '<test>'
    echo Available tests: $tests
    exit 1
  fi
}
