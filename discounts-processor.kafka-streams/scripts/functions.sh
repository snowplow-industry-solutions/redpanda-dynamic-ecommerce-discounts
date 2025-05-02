#!/usr/bin/env bash

check-current-dir() {
  local expected_dir="$ACCEL2_DIR/discounts-processor"
  [ "$PWD" = "$expected_dir" ] || {
    echo You must be in the \"$expected_dir\" directory to run this command
    return 1
  }
}

src() {
  check-current-dir || return 1
  case "$1" in
  "format") ./gradlew spotlessApply ;;
  "test")
    if [ -n "${2:-}" ]; then
      ./gradlew test --tests "com.example.$2"
    else
      ./gradlew clean test
    fi
    ;;
  *) echo "Usage: src [format|test [TestClassName]]" ;;
  esac
}

e2e() {
  check-current-dir || return 1
  t=$(sed 's/-t \(.*\)/\1/' <<<"$@" | cut -f1 -d' ')
  [ "$t" ] && f=e2e.$t || f=e2e
  f=$f x ./e2e/run.sh "$@"
}

log() {
  check-current-dir || return 1
  tail -F private/logs/main.log
}

red() {
  check-current-dir || return 1
  ./redpanda.sh "$@"
}

run() {
  check-current-dir || return 1
  [ "$1" ] || set -- --build
  ./run.sh "$@"
}
