#!/usr/bin/env bash
set -eou pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

REDPANDA_WAIT_TIME=${REDPANDA_WAIT_TIME:-5}
PRODUCER_WAIT_TIME=${PRODUCER_WAIT_TIME:-2}
PROCESSOR_WAIT_TIME=${PROCESSOR_WAIT_TIME:-12}

LOG_FILE="logs/discounts-processor.log"
GENERATED_STRING="Generated discount event"

redpanda_up() {
  echo "Starting Redpanda. Waiting for $REDPANDA_WAIT_TIME seconds..."
  ./redpanda.sh up &>/dev/null
  sleep $REDPANDA_WAIT_TIME
}

redpanda_down() {
  echo "Stopping Redpanda..."
  ./redpanda.sh down &>/dev/null
}

produce_events() {
  echo "Producing events. Waiting for $PRODUCER_WAIT_TIME seconds..."
  ./redpanda.sh produce
  sleep $PRODUCER_WAIT_TIME
}

start_processor_and_wait() {
  echo "Starting processor. Waiting for $PROCESSOR_WAIT_TIME seconds..."
  ./run.sh --build &
  PROCESSOR_PID=$!
  sleep $PROCESSOR_WAIT_TIME
}

check_logs() {
  local expect_logs=$1
  local found_logs=$(
    grep -q "$GENERATED_STRING" "$LOG_FILE"
    echo $?
  )

  if [ $found_logs -eq 0 ] && [ "$expect_logs" = true ] ||
    [ $found_logs -ne 0 ] && [ "$expect_logs" = false ]; then
    echo "✅ Test passed: $([ "$expect_logs" = true ] &&
      echo "Discounts generated for new events" ||
      echo "No discounts generated for historical events")"
  else
    echo "❌ Test failed: $([ "$expect_logs" = true ] &&
      echo "No discounts generated for new events" ||
      echo "Discounts were generated for historical events")"
  fi
}

run_test_scenario() {
  local scenario=$1
  local expect_logs=true

  echo "Testing $scenario events scenario..."

  redpanda_up
  if [ "$scenario" = "historical" ]; then
    expect_logs=false
    produce_events
    start_processor_and_wait
  else
    start_processor_and_wait
    produce_events
  fi
  check_logs $expect_logs
  kill $PROCESSOR_PID
  redpanda_down
}

op=${1:-new}
case $op in
historical | new) run_test_scenario $op ;;
*) echo "Usage: $0 [historical|new]" ;;
esac
