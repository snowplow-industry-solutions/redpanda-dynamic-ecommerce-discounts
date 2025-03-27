#!/usr/bin/env bash

DOCKER_DIR=$PWD
LOGS_DIR=${LOGS_DIR:-$DOCKER_DIR/logs}
SHOW_LOGS=${SHOW_LOGS:-true}
CONFIG_DIR=${CONFIG_DIR:-../config}

check-required-tools() {
  local all_ok=true
  for tool in "$@"; do
    command -v $tool &>/dev/null || {
      log-warn Install $tool before using this option!
      all_ok=false
    }
  done
  $all_ok
}

docker() {
  log-info docker "$@"
  command docker "$@"
}

set-env() {
  local env_file=.env
  local project_dir=..

  if ! [ -f $env_file ] || [ -f .keep-warning ]; then
    project_env_file=$project_dir/$(
      cd $project_dir
      echo ../${PWD##*/}
    )${env_file#./}
    if ! [ -f $env_file ] && [ -f $project_env_file ]; then
      log-info File $project_env_file found. Copying it to $CONFIG_DIR/$env_file ...
      cp $project_env_file $env_file
      rm -f .keep-warning
    elif ! [ -f $env_file ] || [ -f .keep-warning ]; then
      log-warn You forgot to configure the file $env_file!
      log-warn I\'m generating it from $env_file.sample. But ...
      log-warn ... you need to fix this or you\'ll experience some errors when deploying to AWS.
      echo
      touch .keep-warning
      sed 's/false/true/g' $env_file.sample >$env_file
    fi
  fi
}

list-services() {
  yq -r '.services | keys[]' compose.$1.yaml | paste -sd ' ' -
}

list-available-services() {
  declare -F | sed -n 's/.*_\(.*\)-services.*/\1/p'
  exit 0
}

set-services() {
  services=
  options=
  [ $# = 0 ] || {
    for arg in "$@"; do
      ! [[ "$arg" =~ ^- ]] || {
        options="${options:-} $arg"
        continue
      }
      services="${services:-} ${arg%/}"
    done
    services=$(echo -n $services)
    options=$(echo -n $options)
  }
}

show-services() {
  local op=$1
  [ "${services:-}" ] &&
    log-info $op services \($services\) ... ||
    log-info $op all services ...
}

check-required-tools git sed yq docker || {
  log-fatal One or more required tools are missing!
  exit 1
}

mkdir -p $LOGS_DIR
LOG_FILE=$(basename ${BASH_SOURCE[1]} .sh)
LOG_NUMBER=$(ls $LOGS_DIR/$LOG_FILE*.txt 2>/dev/null | sed -e "s/.*$LOG_FILE.//g" -e 's/.txt//g' | sort -n | tail -1 || echo 0)
LOG_NUMBER=$((LOG_NUMBER + 1))
LOG_FILE=$LOGS_DIR/$LOG_FILE.$LOG_NUMBER.txt

source ./libs/log.sh

log-info LOG_FILE: $LOG_FILE

cd $CONFIG_DIR

set-env

env_file=./.env
[ -f $env_file ] || env_file=./.env.sample
source $env_file || exit 1

if [ -f ./setup.sh ]; then
  source ./setup.sh &>/dev/null || {
    log-fatal An error occurred while running $CONFIG_DIR/setup.sh.
    exit 1
  }
fi

! type setup &>/dev/null || setup
for compose in compose.*.yaml; do
  service=$(cut -d. -f2 <<<$compose)
  eval "_$service-services() { list-services $service; }"
done

! [ "${1:-}" = services ] || list-available-services
! [[ "${1:-}" =~ -services$ ]] || {
  f=_$1
  shift
  set -- $($f) "$@"
}
