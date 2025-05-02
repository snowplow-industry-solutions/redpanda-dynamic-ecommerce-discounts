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

trap restart SIGUSR1
trap cleanup SIGTERM SIGINT

echo "[INFO] Service started with PID $$"
echo $$ >"$pid_file"

while true; do
  echo "[INFO] Starting services..."
  docker compose up -d discounts-processor redpanda-console

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

  if [ "$clean_exit" = true ]; then
    break
  fi

  running=true
done

rm -f "$pid_file"
echo "[INFO] Service terminated."
