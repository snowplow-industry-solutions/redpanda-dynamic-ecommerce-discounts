#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

timestamp=${timestamp:-sample}
raw_messages_dir=../extract-redpanda-raw-messages/raw-messages.$timestamp
tsv_debug=../tsv-debug/tsv-debug.sh
all_tsv_file=all.tsv

cat $raw_messages_dir/*.tsv > $all_tsv_file
$tsv_debug --print-field-names $all_tsv_file
