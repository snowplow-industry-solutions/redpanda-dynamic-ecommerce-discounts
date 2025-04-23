import { createBaseTime, runTestScenario, validateTestOutput } from '../common.js';
import { USER_ID, DELAY_SECONDS_BETWEEN_PINGS, durationInSeconds } from './config.js';

const WEBPAGE_ID = 'page1';

export async function createEvents(testName, timeOffset) {
  const baseTime = createBaseTime(timeOffset);
  const events = [];
  const pingCount = 8;

  events.push({
    collector_tstamp: baseTime.toISOString(),
    event_name: 'product_view',
    user_id: USER_ID,
    webpage_id: WEBPAGE_ID,
    product_id: WEBPAGE_ID,
    product_name: 'Test Product',
    product_price: 99.99
  });

  for (let i = 1; i <= pingCount; i++) {
    const timestamp = new Date(baseTime);
    timestamp.setSeconds(timestamp.getSeconds() + (i * DELAY_SECONDS_BETWEEN_PINGS));

    events.push({
      collector_tstamp: timestamp.toISOString(),
      event_name: 'page_ping',
      user_id: USER_ID,
      webpage_id: WEBPAGE_ID
    });
  }

  const expectedOutput = {
    discount: {
      rate: 0.1,
      by_view_time: {
        duration_in_seconds: durationInSeconds(pingCount)
      }
    },
    user_id: USER_ID,
    product_id: WEBPAGE_ID,
    generated_at: baseTime.toISOString()
  };

  return { events, expectedOutput };
}

export async function runTest(timeOffset, force, options) {
  const { outputJson, outputValue, expectedOutput } = await runTestScenario({
    testName: 'SingleProduct',
    testDescription: 'Check single-product test',
    createEvents: (testName) => createEvents(testName, timeOffset),
    timeOffset,
    force,
    userId: USER_ID
  });

  return validateTestOutput(
    outputJson, expectedOutput,
    () =>
        outputValue.user_id === expectedOutput.user_id &&
        outputValue.product_id === expectedOutput.product_id &&
        outputValue.discount.rate === expectedOutput.discount.rate &&
        outputValue.discount.by_view_time.duration_in_seconds === expectedOutput.discount.by_view_time.duration_in_seconds &&
        typeof outputValue.generated_at === 'string' &&
        /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(outputValue.generated_at)
  );
}
