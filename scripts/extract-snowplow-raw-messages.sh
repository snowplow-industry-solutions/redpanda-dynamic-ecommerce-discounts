#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

OUTPUT_DIR=${OUTPUT_DIR:-./raw-messages.$(date +%s)}
KAFKA_BROKER=${KAFKA_BROKER:-localhost:19092}
MAX_MESSAGES=${MAX_MESSAGES:-5}
TOPIC_NAME=${TOPIC_NAME:-'snowplow-enriched-good'}

tools_are_available=true
for tool in rpk jq; do
  command -v $tool &> /dev/null || {
    echo "Required $tool command is not available."
    tools_are_available=false
  }
done

$tools_are_available || exit 1

mkdir -p $OUTPUT_DIR

echo Current directory: $PWD
echo Created output directory: $OUTPUT_DIR
echo Connecting to Kafka broker at $KAFKA_BROKER
echo Consuming messages from topic $TOPIC_NAME
echo Messages will be saved to: $OUTPUT_DIR

messages_json_file="$OUTPUT_DIR/$MAX_MESSAGES-raw-messages.json"

echo Starting to consume messages...

rpk topic consume "$TOPIC_NAME" \
    --brokers "$KAFKA_BROKER" \
    --offset start \
    -n $MAX_MESSAGES \
    > "$messages_json_file"

echo Processing messages...

if [ -f "$messages_json_file" ] && [ -s "$messages_json_file" ]; then
    messages_count=$(jq -rc '.' "$messages_json_file" | wc -l)
    
    jq -rc '.' "$messages_json_file" | jq -rc '. | {offset: .offset, value: .value}' |
    while read -r json; do
        offset=$(echo "$json" | jq -r '.offset')
        tsv_file=$OUTPUT_DIR/${offset}.tsv
        echo "$json" | jq -r '.value' > $tsv_file
        echo Message with offset $offset saved to $tsv_file
    done
    
    echo Original messages JSON file saved to $messages_json_file
    echo Total messages saved: $messages_count
else
    echo Expected to retrieve ${MAX_MESSAGES} from topic but, no messages were retrieved.
fi

SAMPLE_OUTPUT_DIR=${OUTPUT_DIR%.*}.sample
[ -d $SAMPLE_OUTPUT_DIR ] || {
  cp -r $OUTPUT_DIR $SAMPLE_OUTPUT_DIR
  echo Created sample output directory: $SAMPLE_OUTPUT_DIR
}

echo Process completed.
