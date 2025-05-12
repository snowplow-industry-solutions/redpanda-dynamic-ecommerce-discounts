import config from './config.js'
import { promises as fs } from 'fs'
import path from 'path'
import { execAsync, rpk } from './utils.js'

const __dirname = process.cwd()

export const DEFAULT_WAIT_TIME = 10000
export const WINDOW_DURATION_MS = 5 * 60 * 1000

export function parseTimeOffset(timeOffset) {
  if (!timeOffset) {
    return 0
  }

  try {
    if (typeof timeOffset === 'number') {
      return timeOffset
    }

    if (!/^\d{2}:\d{2}:\d{2}$/.test(timeOffset)) {
      throw new Error(`Invalid time offset format: ${timeOffset}. Expected format: HH:MM:SS`)
    }

    const [hours, minutes, seconds] = timeOffset.split(':').map(Number)

    if (isNaN(hours) || hours < 0 || hours > 23) {
      throw new Error(`Invalid hours value: ${hours}`)
    }
    if (isNaN(minutes) || minutes < 0 || minutes > 59) {
      throw new Error(`Invalid minutes value: ${minutes}`)
    }
    if (isNaN(seconds) || seconds < 0 || seconds > 59) {
      throw new Error(`Invalid seconds value: ${seconds}`)
    }

    return hours * 3600 + minutes * 60 + seconds
  } catch (error) {
    console.error('Error parsing time offset:', {
      timeOffset,
      type: typeof timeOffset,
      error: error.message
    })
    throw error
  }
}

export function createBaseTime(timeOffset) {
  const now = new Date()
  const offsetSeconds = parseTimeOffset(timeOffset)
  return new Date(now.getTime() - offsetSeconds * 1000)
}

export async function createTestFiles(
  testName,
  eventData,
  {
    timeOffset = '00:00:00',
    force = false,
    jsonlOnly = false,
    createEventsFile = true,
    createExpectedFile = false
  } = {}
) {
  const dataDir = path.join(__dirname, 'data')
  await fs.mkdir(dataDir, { recursive: true })

  const testFile = path.join(dataDir, `${testName}.jsonl`)
  const expectedFile = path.join(dataDir, `${testName}.json`)
  const shouldCreateNew = force || jsonlOnly || timeOffset !== '00:00:00'

  let events = []

  if (createEventsFile) {
    const fileExists = await fs
      .access(testFile)
      .then(() => true)
      .catch(() => false)
    if (fileExists && !shouldCreateNew) {
      console.log(`Using existing test file: ${path.basename(testFile)}`)
      const content = await fs.readFile(testFile, 'utf8')
      events = content
        .split('\n')
        .filter(line => line.trim())
        .map(line => JSON.parse(line))
    } else {
      console.log(`Creating new events file: ${path.basename(testFile)}`)
      const content = eventData.events.map(event => JSON.stringify(event)).join('\n') + '\n'
      await fs.writeFile(testFile, content)
      events = eventData.events
      createExpectedFile = true
    }
  }

  if (createExpectedFile) {
    console.log(`Creating new expected output file: ${path.basename(expectedFile)}`)
    await fs.writeFile(expectedFile, JSON.stringify(eventData.expectedOutput, null, 2))
  } else {
    console.log(`Using existing expected output file: ${path.basename(expectedFile)}`)
  }

  return { path: testFile, events }
}

export async function calculateWaitTime(events) {
  if (!events || events.length === 0) return DEFAULT_WAIT_TIME

  const lastEventTime = new Date(events[events.length - 1].collector_tstamp)
  const currentTime = new Date()
  const timeDiff = lastEventTime - currentTime

  if (timeDiff > 0) {
    console.log(
      `Events are from the future - waiting ${Math.ceil(
        timeDiff / 1000
      )}s for their time to come...`
    )
    return timeDiff + DEFAULT_WAIT_TIME
  }

  const remainingWindowTime = WINDOW_DURATION_MS - Math.abs(timeDiff)
  if (remainingWindowTime > 0) {
    console.log(
      `Events are from the past - waiting ${Math.ceil(
        remainingWindowTime / 1000
      )}s for window to complete...`
    )
    return remainingWindowTime
  }

  return DEFAULT_WAIT_TIME
}

export async function runTestScenario({
  testName,
  createEvents,
  timeOffset,
  force = false,
  userId = config.USER_ID,
  inputTopic = 'snowplow-enriched-good',
  outputTopic = 'shopper-discounts',
  options = {},
  regenerateOutput = false
}) {
  console.log(`Starting ${testName}...`)

  const { events, expectedOutput } = await createEvents(timeOffset, options)

  const testFileInfo = await createTestFiles(
    testName,
    { events, expectedOutput },
    {
      timeOffset,
      force,
      createEventsFile: !regenerateOutput,
      createExpectedFile: regenerateOutput
    }
  )

  if (regenerateOutput) {
    console.log('Skipping event sending (regenerateOutput mode)')
    return {
      outputJson: null,
      outputValue: null,
      expectedOutput
    }
  }

  const eventCount = testFileInfo.events.length
  console.log(`Sending ${eventCount} events to input topic (${inputTopic})...`)

  await execAsync(
    `cat ${testFileInfo.path} | docker exec -i redpanda rpk topic produce ${inputTopic} -k ${userId}`
  )

  const waitTime = await calculateWaitTime(testFileInfo.events)
  console.log(`Waiting ${Math.ceil(waitTime / 1000)} seconds for processing...`)
  await new Promise(resolve => setTimeout(resolve, waitTime))

  console.log(`Reading output topic (${outputTopic})...`)
  const output = await rpk(`topic consume ${outputTopic} -n 1`)
  const outputJson = JSON.parse(output)
  const outputValue = JSON.parse(outputJson.value)

  return {
    outputJson,
    outputValue,
    expectedOutput
  }
}

export function validateTestOutput(outputJson, expectedOutput, validator) {
  const isValid = validator()
  const outputValue = JSON.parse(outputJson.value)

  const formattedExpected = JSON.parse(JSON.stringify(expectedOutput))

  if (isValid) {
    console.log('✅ Test passed! Output was generated correctly')
    console.log('Output:', JSON.stringify(outputValue, null, 2))
  } else {
    console.log('❌ Test failed! output was not generated as expected')
    console.log('Expected:', JSON.stringify(formattedExpected, null, 2))
    console.log('Received:', JSON.stringify(outputValue, null, 2))
  }

  return isValid
}

export const createProductViewEvent = ({
  timestamp,
  userId,
  webpageId,
  productId,
  productName,
  productPrice
}) => ({
  collector_tstamp: timestamp.toISOString(),
  event_name: 'product_view',
  user_id: userId,
  webpage_id: webpageId,
  product_id: productId,
  product_name: productName,
  product_price: productPrice
})

export const createPagePingEvent = ({ timestamp, userId, webpageId }) => ({
  collector_tstamp: timestamp.toISOString(),
  event_name: 'page_ping',
  user_id: userId,
  webpage_id: webpageId
})

export const createPagePingEvents = (baseTime, userId, webpageId, count, initialOffset = 0) => {
  const events = []
  const timestamp = new Date(
    baseTime.getTime() + initialOffset * 1000 + config.DELAY_SECONDS_TO_FIRST_PAGE_PING * 1000
  )
  let lastTimestamp = timestamp

  for (let i = 0; i < count; i++) {
    const currentTimestamp = new Date(
      timestamp.getTime() + i * config.DELAY_SECONDS_BETWEEN_PINGS * 1000
    )
    lastTimestamp = currentTimestamp
    events.push(
      createPagePingEvent({
        timestamp: currentTimestamp,
        userId,
        webpageId
      })
    )
  }
  return { events, lastTimestamp }
}

export const ValidationRules = {
  ISOString: path => ({
    type: 'custom',
    description: `Validating if '${path}' is a valid ISO 8601 datetime string (YYYY-MM-DDThh:mm:ss.sssZ)`,
    validate: actual => {
      const value = actual[path]
      return (
        typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value)
      )
    }
  })
}

export class Validator {
  constructor(actual, expected) {
    this.actual = actual
    this.expected = expected
    this.isValid = true
  }

  validate(pathOrRule) {
    if (typeof pathOrRule === 'object' && pathOrRule.type === 'custom') {
      const result = pathOrRule.validate(this.actual)
      console.log(`\n${pathOrRule.description}`)
      console.log(`${result ? '✅ PASSED' : '❌ FAILED'}`)
      this.isValid = this.isValid && result
      return this
    }

    if (typeof pathOrRule === 'function') {
      const result = pathOrRule()
      console.log(`Function validation: ${result ? '✅ PASSED' : '❌ FAILED'}`)
      this.isValid = this.isValid && result
      return this
    }

    const getValue = (obj, path) => {
      return path.split('.').reduce((curr, key) => curr?.[key], obj)
    }

    const actualValue = getValue(this.actual, pathOrRule)
    const expectedValue = getValue(this.expected, pathOrRule)
    const result = actualValue === expectedValue

    console.log(`\nValidating path: "${pathOrRule}"`)
    if (result) {
      console.log(`✅ PASSED - Values match`)
    } else {
      console.log(`❌ FAILED - Values differ:`)

      if (actualValue === undefined) {
        console.log(`  ⚠️  Field is missing in actual object`)
      } else if (expectedValue === undefined) {
        console.log(`  ⚠️  Field is missing in expected object`)
      } else {
        console.log(`  Actual:   ${this.formatValue(actualValue)}`)
        console.log(`  Expected: ${this.formatValue(expectedValue)}`)

        if (typeof actualValue !== typeof expectedValue) {
          console.log(
            `  ⚠️  Type mismatch: actual is ${typeof actualValue}, expected is ${typeof expectedValue}`
          )
        } else if (typeof actualValue === 'number') {
          const diff = actualValue - expectedValue
          console.log(`  ⚠️  Numeric difference: ${diff > 0 ? '+' : ''}${diff}`)
        }
      }
    }

    this.isValid = this.isValid && result
    return this
  }

  formatValue(value) {
    if (typeof value === 'object' && value !== null) {
      return JSON.stringify(value, null, 2)
    }
    return String(value)
  }

  result() {
    console.log(
      `\nFinal validation result: ${
        this.isValid ? '✅ ALL TESTS PASSED' : '❌ SOME TESTS FAILED'
      }\n`
    )
    return this.isValid
  }
}

export async function createSummaryFile(events, pathOrTestName) {
  const productSummary = new Map()
  let totalRandomDelays = 0
  let lastProductEndTime = null
  let lastProductId = null
  let currentPingCount = 0
  let currentProductId = null

  events.forEach(event => {
    const currentTimestamp = new Date(event.collector_tstamp).getTime()

    if (event.event_name === 'product_view') {
      currentProductId = event.product_id

      const existingProduct = productSummary.get(currentProductId)
      if (existingProduct) {
        existingProduct.views++
        existingProduct.pingCount.push(0)
      } else {
        productSummary.set(currentProductId, {
          productId: currentProductId,
          productName: event.product_name,
          views: 1,
          durationInSeconds: 0,
          pingCount: [0],
          lastView: event.collector_tstamp,
          delayToNextProduct: []
        })
      }

      if (lastProductEndTime && lastProductId && lastProductId !== currentProductId) {
        const timeDiff = Math.floor((currentTimestamp - lastProductEndTime) / 1000)
        const lastProduct = productSummary.get(lastProductId)
        if (lastProduct) {
          lastProduct.delayToNextProduct.push(timeDiff)
          if (timeDiff > 0) {
            totalRandomDelays += timeDiff
          }
        }
      }

      lastProductId = currentProductId
      lastProductEndTime = currentTimestamp
    } else if (event.event_name === 'page_ping' && currentProductId) {
      const product = productSummary.get(currentProductId)
      if (product) {
        if (product.pingCount.length > 0) {
          product.pingCount[product.pingCount.length - 1]++
        }
        product.lastView = event.collector_tstamp
        product.durationInSeconds = config.durationInSeconds(
          product.pingCount.reduce((sum, count) => sum + count, 0)
        )
        lastProductEndTime = currentTimestamp
      }
    }
  })

  const lastProduct = productSummary.get(lastProductId)
  if (lastProduct && lastProduct.delayToNextProduct.length === 0) {
    lastProduct.delayToNextProduct = [0]
  }

  const summaryArray = Array.from(productSummary.values()).sort((a, b) => {
    if (b.views !== a.views) {
      return b.views - a.views
    }

    const totalPingsA = a.pingCount.reduce((sum, count) => sum + count, 0)
    const totalPingsB = b.pingCount.reduce((sum, count) => sum + count, 0)
    if (totalPingsB !== totalPingsA) {
      return totalPingsB - totalPingsA
    }

    return new Date(b.lastView) - new Date(a.lastView)
  })

  const timestamps = events.map(event => new Date(event.collector_tstamp).getTime())
  const windowStart = new Date(Math.min(...timestamps)).toISOString()
  const windowEnd = new Date(Math.max(...timestamps)).toISOString()

  const totalViewingTime = summaryArray.reduce((sum, product) => sum + product.durationInSeconds, 0)

  const totalTimeWithDelays = totalViewingTime + totalRandomDelays

  const hours = Math.floor(totalTimeWithDelays / 3600)
  const minutes = Math.floor((totalTimeWithDelays % 3600) / 60)
  const seconds = totalTimeWithDelays % 60
  const duration = [
    hours.toString().padStart(2, '0'),
    minutes.toString().padStart(2, '0'),
    seconds.toString().padStart(2, '0')
  ].join(':')

  const summaryObject = {
    products: summaryArray,
    windowStart,
    windowEnd,
    duration,
    totalViewingTime,
    totalRandomDelays,
    totalTimeWithDelays
  }

  const summaryFile = path.isAbsolute(pathOrTestName)
    ? pathOrTestName
    : path.join(__dirname, `data/${pathOrTestName}.summary.json`)

  await fs.writeFile(summaryFile, JSON.stringify(summaryObject, null, 2))
  if (!path.isAbsolute(pathOrTestName)) {
    console.log(`Summary file created: ${path.basename(summaryFile)}`)
  }

  return summaryObject
}
