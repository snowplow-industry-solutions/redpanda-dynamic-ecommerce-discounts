#!/usr/bin/env bash
set -eou pipefail

cd $(dirname $0)

tsv_debug_script=../../scripts/tsv-debug/tsv-debug.sh
data_file=data.tsv
line=${line:-all}

field_value() {
  local all_content=$(grep "^$1:" $data_line_file)
  echo ${all_content#$1:}
}

print_value_for() {
  [ "${1:-}" ] || {
    echo 'print_value_for ?'
    return 1
  }
  case "${2:-}" in
    as-json) echo $1="$(jq . <<< "$(field_value $1)")";;
    *) echo $1=$(field_value $1)
  esac
  echo
}

generate_output() {
  local data_line_file=data.fields.$line

  sed -n ${line}p $data_file |
    $tsv_debug_script --print-field-names > $data_line_file

  echo Generating file data.fields.$line.txt with this content:
  {
    print_value_for event 
    print_value_for event_name
    print_value_for unstruct_event as-json
    print_value_for contexts as-json
  } | tee data.fields.$line.txt

  rm -f $data_line_file
}

if [ $line != all ]
then
  generate_output
else
  for line in $(seq 1 $(wc -l < $data_file))
  do
    generate_output
  done
fi
