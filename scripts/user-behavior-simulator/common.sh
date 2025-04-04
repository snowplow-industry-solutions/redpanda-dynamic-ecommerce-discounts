#!/usr/bin/env bash

docker_dir=${docker_dir:-../../docker}
discounts_processor_logs_dir=${discounts_processor_logs_dir:-../discounts-processor/logs}

check_usage() {
  if [ $# -lt 2 ]; then
    echo "Error: Missing required parameters"
    echo "Usage: $0 <behavior> <mode>"
    echo "  behavior: frequent | long | normal"
    echo "  mode: kafka | ui"
    exit 1
  fi
}

handle_redpanda() {
  case "${1:-}" in
  redpanda)
    group=$1
    shift
    case "${1:-}" in
    up | down)
      $docker_dir/$1.sh $group-services
      exit 0
      ;;
    logs)
      $docker_dir/logs.sh discounts-processor
      exit 0
      ;;
    esac
    ;;
  esac
}

validate_mode() {
  local mode=$1
  case $mode in
  ui | kafka)
    return 0
    ;;
  *)
    echo "Error: Invalid mode: $mode"
    return 1
    ;;
  esac
}

