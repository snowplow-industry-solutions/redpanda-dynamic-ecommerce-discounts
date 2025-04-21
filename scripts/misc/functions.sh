#!/usr/bin/env bash

ACCEL2_SCRIPTS_MISC_DIR=$(cd $(dirname ${BASH_SOURCE[0]}) && pwd)
ACCEL2_DIR=${ACCEL2_SCRIPTS_MISC_DIR%/scripts/misc}

f=$ACCEL2_DIR/discounts-processor/scripts/functions.sh
! [ -f $f ] || source $f
unset f

accel2() {
  case ${1:-cd} in
  cd) cd "$ACCEL2_DIR" ;;
  esac
}

x() {
  $ACCEL2_SCRIPTS_MISC_DIR/x.sh "$@"
}

readme() {
  case "${1:-html}" in
  html) ! [ -f README.html ] || open README.html &>/dev/null ;;
  pdf) ! [ -f README.pdf ] || open README.pdf &>/dev/null ;;
  esac
}

cmd="source ~${ACCEL2_SCRIPTS_MISC_DIR#$HOME}/functions.sh"
grep -q "^$cmd$" ~/.bashrc || {
  echo Adding \'$cmd\' to ~/.bashrc
  echo "$cmd" >>~/.bashrc
}
unset cmd
