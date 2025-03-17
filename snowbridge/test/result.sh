#!/usr/bin/env bash
set -eou pipefail

cd $(dirname $0)

line=${line:-all}

print_data_field() {
  grep -o "Data:.*" | sed 's/^Data://' | jq .
}

generate_output() {
  local output_file=result.$line.txt
  echo Generating $output_file with this content:
  sed -n ${line}p result.txt | print_data_field | tee "$output_file"
}

if [ $line != all ]
then
  generate_output
else
  for line in $(seq 1 $(wc -l < result.txt))
  do
    generate_output
  done
fi
