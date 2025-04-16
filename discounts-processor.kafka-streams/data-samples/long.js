#!/usr/bin/env node

const readline = require('readline');

async function analyzeContinuousViews() {
  const rl = readline.createInterface({
    input: process.stdin
  });

  const events = [];
  for await (const line of rl) {
    if (line.trim()) {
      events.push(JSON.parse(line));
    }
  }

  const eventsByUser = events.reduce((acc, event) => {
    if (!acc[event.user_id]) {
      acc[event.user_id] = [];
    }
    acc[event.user_id].push(event);
    return acc;
  }, {});

  const results = {
    timing_by_user: {},
    user_views: {}
  };

  // Calcular timing para cada usuário separadamente
  for (const [userId, userEvents] of Object.entries(eventsByUser)) {
    const sortedUserEvents = [...userEvents].sort((a, b) =>
      new Date(a.collector_tstamp) - new Date(b.collector_tstamp)
    );

    const firstEvent = sortedUserEvents[0];
    const lastEvent = sortedUserEvents[sortedUserEvents.length - 1];
    const totalDurationSeconds = Math.floor(
      (new Date(lastEvent.collector_tstamp) - new Date(firstEvent.collector_tstamp)) / 1000
    );

    results.timing_by_user[userId] = {
      first_event: firstEvent.collector_tstamp,
      last_event: lastEvent.collector_tstamp,
      duration_seconds: totalDurationSeconds
    };

    const eventsByProduct = userEvents.reduce((acc, event) => {
      if (!acc[event.webpage_id]) {
        acc[event.webpage_id] = [];
      }
      acc[event.webpage_id].push(event);
      return acc;
    }, {});

    const userResults = {};

    for (const [, productEvents] of Object.entries(eventsByProduct)) {
      productEvents.sort((a, b) =>
        new Date(a.collector_tstamp) - new Date(b.collector_tstamp));

      const productView = productEvents.find(e => e.event_name === 'product_view');
      if (!productView) continue;

      const pingEvents = productEvents.filter(e => e.event_name === 'page_ping');
      if (pingEvents.length === 0) continue;

      const startTime = new Date(productView.collector_tstamp);
      const endTime = new Date(pingEvents[pingEvents.length - 1].collector_tstamp);
      const durationSeconds = Math.floor((endTime - startTime) / 1000);

      userResults[productView.product_id] = {
        duration_seconds: durationSeconds,
        page_ping: pingEvents.length
      };
    }

    if (Object.keys(userResults).length > 0) {
      results.user_views[userId] = userResults;
    }
  }

  return results;
}

analyzeContinuousViews()
  .then(results => console.log(JSON.stringify(results, null, 2)))
  .catch(error => {
    console.error(error);
    process.exit(1);
  });
