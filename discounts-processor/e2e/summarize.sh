#!/usr/bin/env bash
set -eou pipefail
cd $(dirname $0)

! [ $# = 0 ] || {
  echo Aborting: a PATH to an input file should be povided!
  exit 1
}

input_file=$1
[[ $1 = /* ]] || input_file=$PWD/$1
[ -f "$input_file" ] || {
  echo File \"$input_file\" not found!
  exit 1
}

output_file=${input_file%.jsonl}.summary-by-${version:-js}.json

echo -n Summarizing $input_file 'using '
if [ "${version:-js}" = kt ]; then
  echo Kotlin implementation...
  (
    echo Generating $output_file...
    cd ..
    ./gradlew summarizeViews --args="$output_file" >/dev/null
  ) <$input_file || echo There was a failure while summarizing...
else
  echo JavaScript implementation...
  [ -d node_modules ] || npm i &>/dev/null
  node index.js --summarize $input_file --output $output_file
fi
