#!/usr/bin/env node

const readline = require("readline");

function parseTimestamp(timestampString) {
  return new Date(timestampString.replace("Z", "+00:00"));
}

function calculateTimeDifferenceSeconds(start, end) {
  return (end - start) / 1000;
}

function createProductOccurrenceMap(lines) {
  if (lines.length === 0) return {};

  const firstTimestamp = parseTimestamp(JSON.parse(lines[0]).collector_tstamp);
  const fiveMinutes = 5 * 60; // 5 minutes in seconds

  const eventsWithinTimeframe = lines.filter((line) => {
    if (!line) return false; // Skip empty lines
    const event = JSON.parse(line);
    const eventTimestamp = parseTimestamp(event.collector_tstamp);
    return (
      calculateTimeDifferenceSeconds(firstTimestamp, eventTimestamp) <=
      fiveMinutes
    );
  });

  const productMap = eventsWithinTimeframe.reduce((acc, line) => {
    const event = JSON.parse(line);
    const productName = event.product_name;
    const eventTimestamp = parseTimestamp(event.collector_tstamp);
    const viewTimeSeconds = calculateTimeDifferenceSeconds(
      firstTimestamp,
      eventTimestamp
    );

    acc[productName] = acc[productName] || { occurrences: 0, total_seconds: 0 };
    acc[productName].occurrences += 1;
    acc[productName].total_seconds += viewTimeSeconds;
    return acc;
  }, {});

  const sortedProducts = Object.entries(productMap).sort(([, a], [, b]) => {
    if (b.occurrences - a.occurrences !== 0) {
      return b.occurrences - a.occurrences;
    }
    return b.total_seconds - a.total_seconds;
  });

  return Object.fromEntries(sortedProducts);
}

async function readFromStdin() {
  const rl = readline.createInterface({
    input: process.stdin,
  });

  const lines = [];
  for await (const line of rl) {
    lines.push(line);
  }
  return lines;
}

async function main() {
  const lines = await readFromStdin();
  const productOccurrenceMap = createProductOccurrenceMap(lines);
  console.log(JSON.stringify(productOccurrenceMap, null, 2));
}

main();
