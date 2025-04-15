#!/usr/bin/env python3

import json
import sys
from datetime import datetime, timedelta

def parse_timestamp(timestamp_str):
    return datetime.fromisoformat(timestamp_str.replace('Z', '+00:00'))

def calculate_time_difference_seconds(start, end):
    return (end - start).total_seconds()

def create_product_occurrence_map(lines):
    if not lines:
        return {}

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
            view_time_seconds = int(time_difference.total_seconds())
            if product_name not in product_map:
                product_map[product_name] = {'occurrences': 1, 'total_seconds': view_time_seconds}
            else:
                product_map[product_name]['occurrences'] += 1
                product_map[product_name]['total_seconds'] += view_time_seconds
        else:
            break

    sorted_products = sorted(product_map.items(), key=lambda item: (-item[1]['occurrences'], -item[1]['total_seconds']))
    return dict(sorted_products)

if __name__ == "__main__":
    lines = [line.strip() for line in sys.stdin]
    product_occurrence_map = create_product_occurrence_map(lines)
    print(json.dumps(product_occurrence_map, indent=2))
