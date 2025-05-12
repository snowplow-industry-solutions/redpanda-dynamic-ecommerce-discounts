#!/usr/bin/env bash

export ACCEL2_SCRIPTS_MISC_DIR=$(cd $(dirname ${BASH_SOURCE[0]}) && pwd)
export ACCEL2_SCRIPTS_DIR=${ACCEL2_SCRIPTS_MISC_DIR%/misc}
export ACCEL2_DIR=${ACCEL2_SCRIPTS_DIR%/scripts}

export COMPOSE_PROJECT_NAME=accel2

export PATH=$ACCEL2_SCRIPTS_DIR/docker:$PATH
export PATH=$ACCEL2_SCRIPTS_MISC_DIR:$PATH

f=$ACCEL2_DIR/discounts-processor/scripts/functions.sh
! [ -f $f ] || source $f
unset f

accel2() {
  case ${1:-cd} in
  cd) cd "$ACCEL2_DIR" ;;
  esac
}

x() {
  $FUNCNAME.sh "$@"
}

build-all-docs() {
  $FUNCNAME.sh
}

readme() {
  case "${1:-html}" in
  html) ! [ -f README.html ] || open README.html &>/dev/null ;;
  pdf) ! [ -f README.pdf ] || open README.pdf &>/dev/null ;;
  esac
}

show-path() {
  echo $PATH | tr : '\n'
}

cmd="source $ACCEL2_SCRIPTS_MISC_DIR/setup.sh"
! [[ "$ACCEL2_SCRIPTS_MISC_DIR" =~ ^$HOME ]] ||
  cmd="source ~${ACCEL2_SCRIPTS_MISC_DIR#$HOME}/setup.sh"
grep -q "^$cmd$" ~/.bashrc || {
  echo Adding \'$cmd\' to ~/.bashrc
  echo "$cmd" >>~/.bashrc
}
unset cmd
