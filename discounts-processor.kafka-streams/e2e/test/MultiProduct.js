import { createBaseTime, runTestScenario, validateTestOutput } from '../common.js';
import { USER_ID, DELAY_SECONDS_TO_FIRST_PING, DELAY_SECONDS_BETWEEN_PINGS, durationInSeconds } from './config.js';

export async function createEvents(testName, timeOffset) {
  const baseTime = createBaseTime(timeOffset);
  const events = [];

  const createProductEvents = (webpageId, productId, productName, price, pingCount, previousEvents) => {
    let startTime;
    if (previousEvents.length === 0) {
      startTime = new Date(baseTime);
    } else {
      const lastEvent = previousEvents[previousEvents.length - 1];
      startTime = new Date(lastEvent.collector_tstamp);
      startTime.setSeconds(startTime.getSeconds() + DELAY_SECONDS_BETWEEN_PINGS);
    }

    const productEvents = [{
      collector_tstamp: startTime.toISOString(),
      event_name: 'product_view',
      user_id: USER_ID,
      webpage_id: webpageId,
      product_id: productId,
      product_name: productName,
      product_price: price
    }];

    for (let i = 1; i <= pingCount; i++) {
      const timestamp = new Date(startTime);
      timestamp.setSeconds(timestamp.getSeconds() + (i * DELAY_SECONDS_BETWEEN_PINGS));

      productEvents.push({
        collector_tstamp: timestamp.toISOString(),
        event_name: 'page_ping',
        user_id: USER_ID,
        webpage_id: webpageId
      });
    }

    events.push(...productEvents);
    return productEvents;
  };

  const product1Events = createProductEvents('page1', 'product1', 'Product 1', 99.99, 9, []);
  const product2Events = createProductEvents('page2', 'product2', 'Product 2', 149.99, 10, product1Events);
  const product3Events = createProductEvents('page3', 'product3', 'Product 3', 199.99, 12, product2Events);

  const expectedOutput = {
    discount: {
      rate: 0.1,
      by_view_time: {
        duration_in_seconds: durationInSeconds(12)
      }
    },
    user_id: USER_ID,
    product_id: 'product3',
    generated_at: baseTime.toISOString()
  };

  return { events, expectedOutput };
}

export async function runTest(timeOffset, force, options) {
  const { outputJson, outputValue, expectedOutput } = await runTestScenario({
    testName: 'MultiProduct',
    testDescription: 'Check multi-product test with different viewing durations',
    createEvents: (testName) => createEvents(testName, timeOffset),
    timeOffset,
    force,
    userId: USER_ID
  });

  return validateTestOutput(
    outputJson,
    expectedOutput,
    () =>
      outputValue.user_id === expectedOutput.user_id &&
      outputValue.product_id === expectedOutput.product_id &&
      outputValue.discount.rate === expectedOutput.discount.rate &&
      outputValue.discount.by_view_time.duration_in_seconds === expectedOutput.discount.by_view_time.duration_in_seconds &&
      typeof outputValue.generated_at === 'string' &&
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(outputValue.generated_at)
  );
}
