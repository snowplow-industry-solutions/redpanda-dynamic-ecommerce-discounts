#!/usr/bin/env bash
set -eou pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

JAR_DIR="build/libs"
JAR_NAME="discounts-processor-1.0-SNAPSHOT.jar"
JAR_PATH="$JAR_DIR/$JAR_NAME"

# Default log file location if not set
CONSOLE_LOG_FILE=${CONSOLE_LOG_FILE:-"private/logs/run.log"}
mkdir -p "$(dirname "$CONSOLE_LOG_FILE")"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

JAVA_PID=""
EXIT_CODE=0
SHUTDOWN_REQUESTED=0

if [ "${1:-}" = "--build" ]; then
  echo -e "${YELLOW}Forcing rebuild...${NC}"
  rm -f "$JAR_PATH"
fi

cleanup() {
  local signal=$1
  if [ $SHUTDOWN_REQUESTED -eq 1 ]; then
    return
  fi
  SHUTDOWN_REQUESTED=1

  echo -e "\n${YELLOW}Received ${signal} signal. Shutting down...${NC}"
  if [ -n "$JAVA_PID" ] && kill -0 $JAVA_PID 2>/dev/null; then
    echo -e "${YELLOW}Stopping Java process (PID: $JAVA_PID)${NC}"
    kill -SIGTERM "$JAVA_PID" 2>/dev/null

    local timeout=30
    local counter=0
    while kill -0 $JAVA_PID 2>/dev/null && [ $counter -lt $timeout ]; do
      sleep 1
      ((counter++))
    done

    if kill -0 $JAVA_PID 2>/dev/null; then
      echo -e "${RED}Process did not terminate gracefully after ${timeout}s, forcing...${NC}"
      kill -9 $JAVA_PID 2>/dev/null
      EXIT_CODE=1
    fi
  fi
}

finish() {
  if [ -n "$JAVA_PID" ]; then
    wait $JAVA_PID 2>/dev/null || true
  fi

  if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}Application terminated successfully${NC}"
  else
    echo -e "${RED}Application terminated with error code $EXIT_CODE${NC}"
  fi
  exit $EXIT_CODE
}

trap 'cleanup SIGTERM' SIGTERM
trap 'cleanup SIGINT' SIGINT
trap 'cleanup SIGQUIT' SIGQUIT
trap 'finish' EXIT

if [ ! -f "$JAR_PATH" ]; then
  echo -e "${YELLOW}JAR not found. Building Discounts Processor...${NC}"

  if [ ! -f "gradlew" ]; then
    echo -e "${RED}Error: gradlew not found${NC}"
    exit 1
  fi

  ./gradlew clean jar

  if [ ! -f "$JAR_PATH" ]; then
    echo -e "${RED}Error: JAR file not found at $JAR_PATH${NC}"
    exit 1
  fi

  echo -e "${GREEN}Build successful!${NC}"
fi

echo -e "${YELLOW}Starting Discounts Processor...${NC}"
echo -e "${YELLOW}Console output will be logged to: $CONSOLE_LOG_FILE${NC}"

JVM_OPTS=(
  "--add-opens=java.base/java.util=ALL-UNNAMED"
  "--add-opens=java.base/java.lang=ALL-UNNAMED"
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
  "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED"
  "-Xmx1g"
  "-Xms512m"
)

# Use process substitution with tee, but in a way that preserves signal handling
exec > >(trap '' INT; tee "$CONSOLE_LOG_FILE")
exec 2>&1

java "${JVM_OPTS[@]}" -jar "$JAR_PATH" &
JAVA_PID=$!

while [ $SHUTDOWN_REQUESTED -eq 0 ] && kill -0 $JAVA_PID 2>/dev/null; do
  wait $JAVA_PID 2>/dev/null || true
  if [ $? -ne 127 ]; then
    EXIT_CODE=$?
    break
  fi
done
