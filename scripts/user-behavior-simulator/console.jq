#!/usr/bin/env -S jq -rf

if .message then
  .message
else
  .
end
