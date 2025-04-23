import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execAsync, rpk } from './utils.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const DEFAULT_WAIT_TIME = 10000;
export const WINDOW_DURATION_MS = 5 * 60 * 1000;

export function parseTimeOffset(timeOffset) {
  if (typeof timeOffset === 'number') {
    return timeOffset;
  }
  const [hours, minutes, seconds] = timeOffset.split(':').map(Number);
  return hours * 3600 + minutes * 60 + seconds;
}

export function createBaseTime(timeOffset) {
  const baseDate = new Date();
  const offsetMs = parseTimeOffset(timeOffset) * 1000;
  return new Date(baseDate.getTime() - offsetMs);
}

export async function createTestFile(testName, eventData, timeOffset = '00:00:00', force = false, jsonlOnly = false) {
  const dataDir = path.join(__dirname, 'data');
  await fs.mkdir(dataDir, { recursive: true });

  const testFile = path.join(dataDir, `${testName}.jsonl`);
  const expectedFile = path.join(dataDir, `${testName}.json`);
  const shouldCreateNew = force || jsonlOnly || timeOffset !== '00:00:00';

  const fileExists = await fs.access(testFile).then(() => true).catch(() => false);
  if (fileExists && !shouldCreateNew) {
    console.log(`Using existing test file: ${testFile}`);
    const content = await fs.readFile(testFile, 'utf8');
    return {
      path: testFile,
      events: content.split('\n')
        .filter(line => line.trim())
        .map(line => JSON.parse(line))
    };
  }

  console.log(`Creating new test files:`);
  console.log(`- Events: ${testFile}`);
  console.log(`- Expected output: ${expectedFile}`);

  const content = eventData.events.map(event => JSON.stringify(event)).join('\n') + '\n';
  await fs.writeFile(testFile, content);

  await fs.writeFile(expectedFile, JSON.stringify(eventData.expectedOutput, null, 2));

  return { path: testFile, events: eventData.events };
}

export async function calculateWaitTime(events) {
  if (!events || events.length === 0) return DEFAULT_WAIT_TIME;

  const lastEventTime = new Date(events[events.length - 1].collector_tstamp);
  const currentTime = new Date();
  const timeDiff = lastEventTime - currentTime;

  if (timeDiff > 0) {
    console.log(`Events are from the future - waiting ${Math.ceil(timeDiff/1000)}s for their time to come...`);
    return timeDiff + DEFAULT_WAIT_TIME;
  }

  const remainingWindowTime = WINDOW_DURATION_MS - Math.abs(timeDiff);
  if (remainingWindowTime > 0) {
    console.log(`Events are from the past - waiting ${Math.ceil(remainingWindowTime/1000)}s for window to complete...`);
    return remainingWindowTime;
  }

  return DEFAULT_WAIT_TIME;
}

export async function runTestScenario({
  testName,
  testDescription,
  createEvents,
  timeOffset,
  force = false,
  userId = 'user1',
  inputTopic = 'snowplow-enriched-good',
  outputTopic = 'shopper-discounts'
}) {
  console.log(`Starting ${testName} (${testDescription})...`);

  const { events, expectedOutput } = await createEvents(testName, timeOffset);
  const testFileInfo = await createTestFile(testName, events, timeOffset, force);

  const eventCount = testFileInfo.events.length;
  console.log(`Sending ${eventCount} events to input topic (${inputTopic})...`);

  await execAsync(`cat ${testFileInfo.path} | docker exec -i redpanda rpk topic produce ${inputTopic} -k ${userId}`);

  const waitTime = await calculateWaitTime(testFileInfo.events);
  console.log(`Waiting ${Math.ceil(waitTime/1000)} seconds for processing...`);
  await new Promise(resolve => setTimeout(resolve, waitTime));

  console.log(`Reading output topic (${outputTopic})...`);
  const output = await rpk(`topic consume ${outputTopic} -n 1`);
  const outputJson = JSON.parse(output);
  const outputValue = JSON.parse(outputJson.value);

  return {
    outputJson,
    outputValue,
    expectedOutput
  };
}

export async function createExpectedOutputFile(testName, expectedOutput) {
  const dataDir = path.join(__dirname, 'data');
  const outputFile = path.join(dataDir, `${testName}.json`);

  console.log(`Creating expected output file: ${outputFile}`);
  const content = JSON.stringify(expectedOutput, null, 2);
  await fs.writeFile(outputFile, content);
}

export function validateTestOutput(outputJson, expectedOutput, validator) {
  const isValid = validator();
  const outputValue = JSON.parse(outputJson.value);

  const formattedExpected = JSON.parse(JSON.stringify(expectedOutput));

  if (isValid) {
    console.log('✅ Test passed! Output was generated correctly');
    console.log('Output:', JSON.stringify(outputValue, null, 2));
  } else {
    console.log('❌ Test failed! output was not generated as expected');
    console.log('Expected:', JSON.stringify(formattedExpected, null, 2));
    console.log('Received:', JSON.stringify(outputValue, null, 2));
  }

  return isValid;
}
