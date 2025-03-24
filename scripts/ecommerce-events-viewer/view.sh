#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

raw_messages_base_dir=../extract-redpanda-raw-messages
tsv_debug=../tsv-debug/tsv-debug.sh

timestamp=$({
  ls -d $raw_messages_base_dir/raw-messages.[1-9]* 2> /dev/null |
  sed 's/\(.*\/raw-messages\.\)//g'|
  sort -r | head -1
} || echo sample)
raw_messages_dir=${raw_messages_base_dir}/raw-messages.$timestamp

all_tsv_file=all.tsv

echo Reading tsv files from $raw_messages_dir ...
cat $raw_messages_dir/*.tsv > $all_tsv_file

echo Printing records and fields in file $all_tsv_file ...
$tsv_debug --print-field-names $all_tsv_file
