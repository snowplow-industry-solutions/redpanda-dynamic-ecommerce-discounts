#!/usr/bin/env -S jq -s -f

# Extract hour, minute, second, and milliseconds from timestamp string
def extract_time($ts):
  ($ts | split(":")[0][-2:] | tonumber) * 3600 +
  ($ts | split(":")[1] | tonumber) * 60 +
  ($ts | split(":")[2][0:2] | tonumber) +
  ($ts | split(".")[1][0:3] | tonumber) / 1000;

# Process the input data
. as $events |

# Get the first event's timestamp in seconds
($events[0].collector_tstamp) as $first_ts |
extract_time($first_ts) as $first_time |

# Process all events and count product occurrences
reduce $events[] as $event (
  {};

  # Calculate time difference in seconds
  ($event.collector_tstamp) as $curr_ts |
  extract_time($curr_ts) as $curr_time |
  ($curr_time - $first_time) as $seconds_elapsed |

  # Only include events within 5 minutes (300 seconds)
  if $seconds_elapsed <= 300 then
    $event.product_name as $product_name |

    # Update product stats
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

# Sort by occurrences (descending) and then by total_seconds (descending)
to_entries |
sort_by(-(.value.occurrences), -(.value.total_seconds)) |
from_entries
