#!/usr/bin/env bash
set -eou pipefail
cd $ACCEL2_DIR

echo Starting build all docs in $PWD...$'\n'

mapfile -t builds < <(
  find . -type f -name README.sh
  find . -type l -path './docs/**/build.sh'
)

for build in "${builds[@]}"
do
  case "$build" in
    *private/* | \
    */user-behavior-simulator/README.sh | \
    */common/README.sh)
      echo Skipping $build...
      ;;
    */README.sh | \
    *.build.sh)
      echo Running $build...
      $build || :
      ;;
  esac
  echo
done

echo All documents were built.
