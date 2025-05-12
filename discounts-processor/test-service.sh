#!/usr/bin/env bash
cd $(dirname $0)

export COMPOSE_PROJECT_NAME=accel2

script_name=$(basename "$0" .sh)
script_dir=$(dirname "$(realpath "$0")")
pid_file="${script_dir}/${script_name}.pid"

running=true

restart() {
  running=false
}

cleanup() {
  running=false
  clean_exit=true
}

save-log() {
  local log=private/logs/disconts-processor.log
  local log_with_testname=${log%.log}.$testname.log
  ! [ -f $log ] || {
    echo Copying $log to $log_with_testname...
    cp $log $log_with_testname
  }
}

trap restart SIGUSR1
trap cleanup SIGTERM SIGINT

echo "[INFO] Service started with PID $$"
echo $$ >"$pid_file"

build=
[ "${1:-}" = --build ] && build=--build

while true; do
  echo "[INFO] Starting services..."
  docker compose up -d $build discounts-processor redpanda-console

  echo "[INFO] Displaying logs..."
  setsid docker compose logs -f discounts-processor &
  logs_pid=$!
  logs_pgid=$(ps -o pgid= $logs_pid | tr -d ' ')

  while $running; do
    sleep 1
  done

  echo "[INFO] Stopping logs..."
  kill -- -"$logs_pgid"
  wait "$logs_pid" 2>/dev/null

  echo "[INFO] Shutting down services..."
  docker compose down -v --remove-orphans discounts-processor redpanda-console redpanda

  cfg=./.$(basename $0 .sh)
  source $cfg &>/dev/null

  if [ "$clean_exit" = true ]; then
    break
  fi

  [ "${testname:-}" ] && save-log

  running=true
done

[ "${testname:-}" ] && save-log

rm -f "$pid_file"
echo "[INFO] Service terminated."
