#!/usr/bin/env bash
cd $(dirname $0)

export COMPOSE_PROJECT_NAME=accel2

script_name=$(basename "$0" .sh)
pid_file=./.$script_name.pid
cfg_file=./.$script_name

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

check-pid_file() {
  [ -f $pid_file ] || {
    echo Aborting: file $pid_file not found!
    exit 1
  }
}

case "${1:-}" in
start-services)
  running=true

  trap restart SIGUSR1
  trap cleanup SIGTERM SIGINT

  echo "Service started with PID $$"
  echo $$ >"$pid_file"

  shift
  ! [ "${1:-}" = '--build' ] || echo "rebuild_discounts_processor=1" >>$cfg_file

  while true; do
    if source $cfg_file &>/dev/null; then
      echo File $cfg_file loaded.
    fi

    ! [ "${rebuild_discounts_processor:-}" ] || {
      echo Removing discounts-processor container since it needs to be rebuild...
      docker rmi $COMPOSE_PROJECT_NAME-discounts-processor

      echo Removing rebuild_discounts_processor flag from $cfg_file and memory...
      grep -v rebuild_discounts_processor $cfg_file | sponge $cfg_file
      unset rebuild_discounts_processor
    }

    echo "Starting services..."
    docker compose up -d discounts-processor redpanda-console

    echo "Displaying logs..."
    setsid docker compose logs -f discounts-processor &
    logs_pid=$!
    logs_pgid=$(ps -o pgid= $logs_pid | tr -d ' ')

    while $running; do
      sleep 1
    done

    echo "Stopping logs..."
    kill -- -"$logs_pgid"
    wait "$logs_pid" 2>/dev/null

    echo "Shutting down services..."
    docker compose down -v --remove-orphans discounts-processor redpanda-console redpanda

    if [ "$clean_exit" = true ]; then
      break
    fi
    [ "${testname:-}" ] && save-log

    running=true
  done

  [ "${testname:-}" ] && save-log

  rm -f "$pid_file"
  echo "Service terminated."
  ;;
restart-services)
  check-pid_file
  kill -SIGUSR1 $(<"$pid_file")
  ;;
stop-services)
  check-pid_file
  kill -SIGTERM $(<"$pid_file")
  ;;
reconfigure)
  set-op() {
    op=${1:-}
    if ! [ "${op:-}" ] || ! [ "$op" = "true" -o "$op" = "false" ]; then
      echo You shoud specify true or false!
      exit 1
    fi
  }
  echo "rebuild_discounts_processor=1" >>$cfg_file
  props_file=src/main/resources/application.properties
  shift
  case "${1:-}" in
  continuous-view)
    shift
    set-op ${1:-}
    sed -i "s/\(processor.continuous-view.enabled=\).*/\1$op/g" $props_file
    ;;
  most-viewed)
    shift
    set-op ${1:-}
    sed -i "s/\(processor.most-viewed.enabled=\).*/\1$op/g" $props_file
    ;;
  discount-event-sender)
    shift
    set-op ${1:-}
    sed -i "s/\(processor.discount-event-sender.enabled=\).*/\1$op/g" $props_file
    ;;
  *)
    echo Aborting: reconfigure option \"$1\" is invalid!
    exit 1
    ;;
  esac
  ;;
run)
  shift
  test_name=${1:-}
  case "$test_name" in
  SingleProduct | \
    MultiProduct | \
    MostViewedMultipleViewsPerProduct)
    shift
    [ "${1:-}" ] && now=true || now=false
    if $now; then
      ./e2e/run.sh now -f $PWD/e2e/data/$test_name.jsonl
      # TODO:
      # ./e2e/run.sh -t $test_name --check-results-only
    else
      f=e2e.$test_name x.sh ./e2e/run.sh -t $test_name
    fi
    log=private/logs/discounts-processor
    new_log=$log.$test_name
    ! $now || new_log=$log.$test_name.now
    log=$log.log
    new_log=$new_log.log
    echo Copying $log to $new_log...
    cp $log $new_log
    ;;
  *)
    echo Aborting: run test_name \"$test_name\" is invalid!
    exit 1
    ;;
  esac
  ;;
*)
  echo Aborting: $0 option \"$1\" is invalid!
  exit 1
  ;;
esac
