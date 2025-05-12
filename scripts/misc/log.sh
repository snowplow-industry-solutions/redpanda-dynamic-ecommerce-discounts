#!/usr/bin/env bash

SHOW_LOGS=${SHOW_LOGS:-true}
LOGS_DIR=${LOGS_DIR:-logs}
LOG_FILE=${LOG_FILE:-$LOGS_DIR/output.txt}
if $SHOW_LOGS; then
  exec > >(tee -a $LOG_FILE) 2>&1
else
  exec >$LOG_FILE 2>&1
fi

unset -f log
log() {
  case "${1:-}" in
  info)
    echo -n "[INFO] "
    ;;
  warning)
    echo -n "[WARNING] "
    ;;
  error)
    echo -n "[ERROR] "
    ;;
  fatal)
    echo -n "[FATAL] "
    ;;
  esac
  shift
  echo "$@"
}

for level in info warn error fatal; do
  eval "log-$level() { log $level "\$@"; }"
done
