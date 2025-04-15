#!/usr/bin/env bash

readme-build() {
  local project_dir=${BASH_SOURCE[0]%/docs/common/functions.sh}
  local docs_dir=$project_dir/docs
  local readme_dir=$PWD
  local dest_dir=$docs_dir/${readme_dir##*/}
  local sync_fn=$1

  [ -d "$docs_dir" ] || return 1
  [ -f README.adoc ] || return 1

  echo Copying ./README.adoc to $dest_dir
  mkdir -p $dest_dir
  cp README.adoc $dest_dir
  ! [ "$sync_fn" ] || $sync_fn $dest_dir

  cd $dest_dir
  ln -sf ../common/build.sh
  ./build.sh

  cd $OLDPWD

  echo Copying 'README.{html,pdf}' and '*.css' files from $dest_dir/ to .
  cp $dest_dir/README.{html,pdf} .
  cp $dest_dir/*.css .

  echo Removing $dest_dir
  rm -rf $dest_dir
}

open-readme-html() {
  ! [ -f README.html ] || open README.html &>/dev/null
}

open-readme-pdf() {
  ! [ -f README.pdf ] || open README.pdf &>/dev/null
}
