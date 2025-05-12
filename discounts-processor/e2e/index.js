import yargs from 'yargs'
import { hideBin } from 'yargs/helpers'
import fs from 'fs/promises'
import path from 'path'
import { fileURLToPath } from 'url'
import { createBaseTime, createTestFiles, runTestScenario, createSummaryFile } from './common.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const TEST_DIR = path.join(__dirname, 'test')
const DATA_DIR = path.join(__dirname, 'data')

async function findTestFiles() {
  await fs.mkdir(TEST_DIR, { recursive: true })
  await fs.mkdir(DATA_DIR, { recursive: true })

  const files = await fs.readdir(TEST_DIR)
  const testFiles = files.filter(file => file.endsWith('.js'))

  const tests = {}
  for (const file of testFiles) {
    const testName = path.basename(file, '.js')
    const module = await import(path.join(TEST_DIR, file))

    if (!module.createEvents || !module.validateTestOutput) {
      console.warn(`Warning: Required exports not found in ${file}`)
      continue
    }

    tests[testName] = {
      createEvents: module.createEvents,
      validateTestOutput: module.validateTestOutput
    }
  }

  return tests
}

async function readEventsFromFile(filePath) {
  const absolutePath = path.isAbsolute(filePath) ? filePath : path.join(__dirname, filePath)

  const content = await fs.readFile(absolutePath, 'utf8')
  return content
    .split('\n')
    .filter(line => line.trim())
    .map(line => JSON.parse(line))
}

async function main() {
  const availableTests = await findTestFiles()
  const testNames = Object.keys(availableTests)

  if (testNames.length === 0) {
    console.error('No test files found in the test directory.')
    process.exit(1)
  }

  const argv = yargs(hideBin(process.argv))
    .option('test', {
      alias: 't',
      type: 'string',
      description: `Test to run (${testNames.join(', ')})`,
      demandOption: false,
      choices: testNames
    })
    .option('timeOffset', {
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
    .option('showOnly', {
      alias: 's',
      type: 'boolean',
      description: 'Show JSONL content without generating new file',
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
    .option('maxViewsPerProduct', {
      alias: 'x',
      type: 'number',
      description: 'Maximum number of views per product',
      default: 5
    })
    .option('minViewsPerProduct', {
      alias: 'n',
      type: 'number',
      description: 'Minimum number of views per product',
      default: 0
    })
    .option('regenerateOutput', {
      alias: 'r',
      type: 'boolean',
      description: 'Regenerate output files without regenerating events',
      default: false
    })
    .option('summarize', {
      alias: 'z',
      type: 'string',
      description: 'Generate summary for events file (provide path to events file)',
      default: null
    })
    .option('output', {
      alias: 'o',
      type: 'string',
      description: 'Output file path for summary (required when using --summarize)',
      default: null
    })
    .help()
    .alias('help', 'h')
    .strict()
    .fail((msg, err, yargs) => {
      if (err) throw err
      console.error('Error:', msg)
      console.error('Use --help to see available options')
      process.exit(1)
    })
    .parse()

  if (!argv.summarize && !argv.test) {
    console.error('Error: Either --test or --summarize must be provided')
    console.error('Use --help to see available options')
    process.exit(1)
  }

  if (argv.summarize) {
    if (!argv.output) {
      console.error('Error: --output is required when using --summarize')
      console.error('Use --help to see available options')
      process.exit(1)
    }

    try {
      const events = await readEventsFromFile(argv.summarize)
      console.log(`Generating ${argv.output}...`)
      await createSummaryFile(events, argv.output)
      return
    } catch (error) {
      console.error('Error generating summary:', error)
      process.exit(1)
    }
  }

  const test = availableTests[argv.test]
  if (!test) {
    console.error(`Test "${argv.test}" not found`)
    process.exit(1)
  }

  try {
    if (argv.timeOffset !== '00:00:00') {
      console.log('Events will be generated (in the past) with a time offset of', argv.timeOffset)
    }

    const baseTime = createBaseTime(argv.timeOffset)
    const options = {
      maxProducts: argv.maxProducts,
      maxPings: argv.maxPings,
      maxViewsPerProduct: argv.maxViewsPerProduct,
      minViewsPerProduct: argv.minViewsPerProduct
    }

    const testFile = path.join(DATA_DIR, `${argv.test}.jsonl`)
    const expectedFile = path.join(DATA_DIR, `${argv.test}.json`)
    const filesExist = await fs
      .access(testFile)
      .then(() => fs.access(expectedFile))
      .then(() => true)
      .catch(() => false)

    if (filesExist) {
      console.log('Test files already exist.')
    } else {
      console.log('Test files do not exist. They will be generated.')
    }

    if (argv.showOnly || argv.jsonlOnly) {
      if (argv.showOnly && filesExist) {
        console.log(`Events file: ${path.basename(testFile)}`)
        console.log(`Expected output file: ${path.basename(expectedFile)}`)
        return
      }
      const { events, expectedOutput } = await test.createEvents(baseTime, options)
      await createTestFiles(
        argv.test,
        { events, expectedOutput },
        {
          timeOffset: argv.timeOffset,
          force: argv.force,
          jsonlOnly: argv.jsonlOnly
        }
      )
      return
    }

    let createEvents = test.createEvents
    if (filesExist) {
      const eventsContent = await fs.readFile(testFile, 'utf8')
      const existingEvents = eventsContent
        .split('\n')
        .filter(line => line.trim())
        .map(line => JSON.parse(line))
      const expectedOutput = JSON.parse(await fs.readFile(expectedFile, 'utf-8'))
      console.log('Using existing content to run test...')
      createEvents = async () => {
        if (argv.regenerateOutput) {
          return test.createEvents(baseTime, options, existingEvents)
        }
        return { existingEvents, expectedOutput }
      }
    }

    const { outputValue, expectedOutput } = await runTestScenario({
      testName: argv.test,
      createEvents,
      timeOffset: argv.timeOffset,
      force: argv.force,
      options,
      regenerateOutput: argv.regenerateOutput
    })

    if (argv.regenerateOutput) {
      return
    }

    const isValid = test.validateTestOutput(outputValue, expectedOutput)

    if (isValid) {
      console.log('Expected is equal to Received')
      console.log('Received:', JSON.stringify(outputValue, null, 2))
    } else {
      console.log('Expected is not equal to Received')
      console.log('Expected:', JSON.stringify(expectedOutput, null, 2))
      console.log('Received:', JSON.stringify(outputValue, null, 2))
      process.exit(1)
    }
  } catch (error) {
    console.error('Test execution failed:', error)
    process.exit(1)
  }
}

main().catch(console.error)
