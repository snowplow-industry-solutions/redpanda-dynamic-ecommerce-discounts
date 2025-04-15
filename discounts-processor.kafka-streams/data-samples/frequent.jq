#!/usr/bin/env -S jq -s -f

def extract_time($ts):
  ($ts | split(":")[0][-2:] | tonumber) * 3600 +
  ($ts | split(":")[1] | tonumber) * 60 +
  ($ts | split(":")[2][0:2] | tonumber) +
  ($ts | split(".")[1][0:3] | tonumber) / 1000;

. as $events |

($events[0].collector_tstamp) as $first_ts |
extract_time($first_ts) as $first_time |

reduce $events[] as $event (
  {};

  ($event.collector_tstamp) as $curr_ts |
  extract_time($curr_ts) as $curr_time |
  ($curr_time - $first_time | floor) as $seconds_elapsed |

  if $seconds_elapsed <= 300 then
    $event.product_name as $product_name |

    if has($product_name) then
      .[$product_name].occurrences += 1 |
      .[$product_name].total_seconds += $seconds_elapsed
    else
      .[$product_name] = {
        "occurrences": 1,
        "total_seconds": $seconds_elapsed
      }
    end
  else
    .
  end
) |

to_entries |
sort_by(-(.value.occurrences), -(.value.total_seconds)) |
from_entries
