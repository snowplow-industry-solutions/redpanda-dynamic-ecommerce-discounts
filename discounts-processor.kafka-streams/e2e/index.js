import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execAsync, rpk } from './utils.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const USER_ID = 'user1';
const WEBPAGE_ID = 'page1';
const INPUT_TOPIC = 'snowplow-enriched-good';
const OUTPUT_TOPIC = 'shopper-discounts';
const DEFAULT_WAIT_TIME = 10000;
const WINDOW_DURATION_MS = 5 * 60 * 1000;

async function getTestEvents(testName) {
  const filePath = path.join(__dirname, `${testName}.jsonl`);
  try {
    return await fs.readFile(filePath, 'utf8');
  } catch (error) {
    if (error.code === 'ENOENT') {
      return null;
    }
    throw error;
  }
}

async function createEventsFile(events, filePath) {
  const content = events.map(event => JSON.stringify(event)).join('\n');
  await fs.writeFile(filePath, content);
}

async function createTestEvents(testName, timeOffset) {
  const baseTime = new Date();
  baseTime.setSeconds(baseTime.getSeconds() + timeOffset);

  const events = [];

  // Initial product view event
  events.push({
    collector_tstamp: new Date(baseTime).toISOString(),
    event_name: 'product_view',
    user_id: USER_ID,
    webpage_id: WEBPAGE_ID,
    product_id: WEBPAGE_ID,
    product_name: 'Test Product',
    product_price: 99.99
  });

  // Generate page_ping events
  for (let i = 1; i <= 9; i++) {
    const timestamp = new Date(baseTime);
    timestamp.setSeconds(timestamp.getSeconds() + (i * 10));

    events.push({
      collector_tstamp: timestamp.toISOString(),
      event_name: 'page_ping',
      user_id: USER_ID,
      webpage_id: WEBPAGE_ID
    });
  }

  return events;
}

function parseTimeOffset(timeStr) {
  const [hours, minutes, seconds] = timeStr.split(':').map(Number);
  return (hours * 3600) + (minutes * 60) + seconds;
}

async function calculateWaitTime(events, isNewFile) {
  if (events.length === 0) return DEFAULT_WAIT_TIME;
  if (isNewFile) return WINDOW_DURATION_MS;

  const firstEventTime = new Date(events[0].collector_tstamp);
  const currentTime = new Date();

  if (firstEventTime > currentTime) {
    console.log('Events are from the future - waiting for their time to come...');
    return firstEventTime - currentTime + DEFAULT_WAIT_TIME;
  } else {
    console.log('Events are from the past - minimal wait time needed');
    return DEFAULT_WAIT_TIME;
  }
}

async function runTest(testName, timeOffset, forceNewFile = false) {
  try {
    console.log(`Starting test "${testName}"...`);

    let eventsContent = forceNewFile ? null : await getTestEvents(testName);
    const tempFile = path.join(__dirname, `${testName}-temp.jsonl`);
    let events = [];

    if (!eventsContent) {
      console.log('Creating new test file...');
      events = await createTestEvents(testName, timeOffset);
      await createEventsFile(events, tempFile);
    } else {
      console.log('Using existing test file...');
      await fs.writeFile(tempFile, eventsContent);
      events = eventsContent
        .split('\n')
        .map(line => JSON.parse(line));
    }

    console.log('Sending events to input topic...');
    await execAsync(`cat ${tempFile} | docker exec -i redpanda rpk topic produce ${INPUT_TOPIC} -k ${USER_ID}`);

    await fs.unlink(tempFile);

    const waitTime = await calculateWaitTime(events, !eventsContent);
    console.log(`Waiting ${Math.ceil(waitTime/1000)} seconds for processing...`);
    await new Promise(resolve => setTimeout(resolve, waitTime));

    console.log('Reading output topic...');
    const output = await rpk(`topic consume ${OUTPUT_TOPIC} -n 1`);
    const outputJson = JSON.parse(output);
    const discountEvent = JSON.parse(outputJson.value);

    if (outputJson.key === USER_ID &&
        discountEvent.user_id === USER_ID &&
        discountEvent.product_id === WEBPAGE_ID &&
        discountEvent.discount &&
        discountEvent.discount.rate === 0.1) {
      console.log('✅ Test passed! Discount was generated correctly');
      console.log('Output:', JSON.stringify(outputJson, null, 2));
    } else {
      console.log('❌ Test failed! Discount was not generated as expected');
      console.log('Expected:', {
        key: USER_ID,
        user_id: USER_ID,
        product_id: WEBPAGE_ID,
        discount: { rate: 0.1 }
      });
      console.log('Received:', JSON.stringify(outputJson, null, 2));
    }
  } catch (error) {
    console.error('Test failed with error:', error);
    process.exit(1);
  }
}

async function main() {
  const argv = yargs(hideBin(process.argv))
    .option('test', {
      alias: 't',
      type: 'string',
      description: 'Test name to be executed',
      default: 'runTest'
    })
    .option('jsonl-only', {
      alias: 'j',
      type: 'boolean',
      description: 'Only creates the JSONL file without running the test',
      default: false
    })
    .option('time-offset', {
      alias: 'o',
      type: 'string',
      description: 'Time offset in format HH:MM:SS',
      default: '00:00:00'
    })
    .option('force', {
      alias: 'f',
      type: 'boolean',
      description: 'Force creation of new test file even if it already exists',
      default: false
    })
    .help()
    .alias('help', 'h')
    .strict()
    .fail((msg, err, yargs) => {
      if (err) throw err;
      console.error('Error:', msg);
      console.error('Use --help to see available options');
      process.exit(1);
    })
    .parse();

  const testName = argv.test;
  const timeOffset = parseTimeOffset(argv.timeOffset);

  if (argv.jsonlOnly) {
    console.log('Mode: JSONL generation only');
    await createTestEvents(testName, timeOffset);
  } else {
    console.log('Mode: Full test execution');
    await runTest(testName, timeOffset, argv.force);
  }
}

main().catch(console.error);
