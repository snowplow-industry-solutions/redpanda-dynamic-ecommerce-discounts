#!/usr/bin/env python3

import json
import sys
from datetime import datetime, timedelta

def parse_timestamp(timestamp_str):
    """Parses an ISO 8601 timestamp string and returns a datetime object."""
    return datetime.fromisoformat(timestamp_str.replace('Z', '+00:00'))

def calculate_time_difference_seconds(start, end):
    """Calculates the time difference between two datetime objects in seconds."""
    return (end - start).total_seconds()

def create_product_occurrence_map(lines):
    """
    Creates a map of products ordered by occurrence within a 5-minute window.
    If two products have the same occurrence count, orders by total view time.

    Args:
        lines (list): A list of JSON strings, each representing an event.

    Returns:
        dict: A dictionary where keys are product names and values are dictionaries
              containing 'occurrences' and 'total_seconds'.
    """

    if not lines:
        return {}  # Empty input, return empty dict

    # Parse the timestamp from the first line to define the 5-minute window
    first_line = json.loads(lines[0])
    first_timestamp = parse_timestamp(first_line['collector_tstamp'])
    five_minutes = timedelta(minutes=5)

    product_map = {}

    for line in lines:
        event = json.loads(line)
        event_timestamp = parse_timestamp(event['collector_tstamp'])
        time_difference = event_timestamp - first_timestamp

        if time_difference <= five_minutes:
            product_name = event['product_name']
            view_time_seconds = time_difference.total_seconds()
            if product_name not in product_map:
                product_map[product_name] = {'occurrences': 1, 'total_seconds': view_time_seconds}
            else:
                product_map[product_name]['occurrences'] += 1
                product_map[product_name]['total_seconds'] += view_time_seconds
        else:
            break

    # Sort products by occurrences (descending) and then by total_seconds (descending)
    sorted_products = sorted(product_map.items(), key=lambda item: (-item[1]['occurrences'], -item[1]['total_seconds']))
    return dict(sorted_products)

if __name__ == "__main__":
    lines = [line.strip() for line in sys.stdin]
    product_occurrence_map = create_product_occurrence_map(lines)
    print(json.dumps(product_occurrence_map, indent=2))
