#!/usr/bin/env bash

env_file=.env
[ -f $env_file ] || env_file=.env.sample
source ./$env_file
