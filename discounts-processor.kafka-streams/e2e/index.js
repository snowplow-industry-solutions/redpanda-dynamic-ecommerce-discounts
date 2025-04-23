import yargs from 'yargs';
import { hideBin } from 'yargs/helpers';
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';
import { createTestFile } from './common.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TEST_DIR = path.join(__dirname, 'test');
const DATA_DIR = path.join(__dirname, 'data');

async function findTestFiles() {
  await fs.mkdir(TEST_DIR, { recursive: true });
  await fs.mkdir(DATA_DIR, { recursive: true });

  const files = await fs.readdir(TEST_DIR);
  const testFiles = files.filter(file => file.endsWith('.js'));

  const tests = {};
  for (const file of testFiles) {
    const testName = path.basename(file, '.js');
    const module = await import(path.join(TEST_DIR, file));

    if (!module.createEvents || !module.runTest) {
      console.warn(`Warning: Required exports not found in ${file}`);
      continue;
    }

    tests[testName] = {
      createEvents: module.createEvents,
      runTest: module.runTest
    };
  }

  return tests;
}

async function main() {
  const availableTests = await findTestFiles();
  const testNames = Object.keys(availableTests);

  if (testNames.length === 0) {
    console.error('No test files found in the test directory.');
    process.exit(1);
  }

  const argv = yargs(hideBin(process.argv))
    .option('test', {
      alias: 't',
      type: 'string',
      description: `Test to run (${testNames.join(', ')})`,
      demandOption: true,
      choices: testNames
    })
    .option('timeOffset', {
      alias: 'o',
      type: 'string',
      description: 'Time offset (HH:MM:SS)',
      default: '00:00:00'
    })
    .option('force', {
      alias: 'f',
      type: 'boolean',
      description: 'Force creation of new test file',
      default: false
    })
    .option('jsonlOnly', {
      alias: 'j',
      type: 'boolean',
      description: 'Only generate JSONL file',
      default: false
    })
    .option('maxProducts', {
      alias: 'p',
      type: 'number',
      description: 'Maximum number of products to include in test',
      default: 10
    })
    .option('maxPings', {
      alias: 'm',
      type: 'number',
      description: 'Maximum number of pings per product',
      default: 7
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

  const test = availableTests[argv.test];
  if (!test) {
    console.error(`Test "${argv.test}" not found`);
    process.exit(1);
  }

  try {
    if (argv.timeOffset !== '00:00:00') {
      console.log('Events will be generated (in the past) with a time offset of', argv.timeOffset);
    }

    if (argv.jsonlOnly) {
      const { events, expectedOutput } = await test.createEvents(argv.test, argv.timeOffset);
      await createTestFile(argv.test, { events, expectedOutput }, argv.timeOffset, argv.force, argv.jsonlOnly);
      console.log(`JSONL file generated successfully for test "${argv.test}"`);
      return;
    }

    const options = {
      maxProducts: argv.maxProducts,
      maxPings: argv.maxPings
    };

    await test.runTest(argv.timeOffset, argv.force, options);
  } catch (error) {
    console.error('Test execution failed:', error);
    process.exit(1);
  }
}

main().catch(console.error);
