import {
  ALL_PRODUCTS,
  generateBaseSummaryAndOutput,
  generateProductViewEvents,
  validateMostViewedTestOutput
} from '../common-most-viewed.js'

function generateNewEvents(baseTime, options) {
  const { maxProducts = 10 } = options
  const numProducts = Math.min(maxProducts, ALL_PRODUCTS.length)
  const selectedProducts = [...ALL_PRODUCTS].sort(() => Math.random() - 0.5).slice(0, numProducts)

  const { events } = generateProductViewEvents(baseTime, options, selectedProducts)
  return events.sort((a, b) => new Date(a.collector_tstamp) - new Date(b.collector_tstamp))
}

export async function createEvents(baseTime, options, existingEvents = null) {
  if (existingEvents) {
    console.log('Using existing events to regenerate expected output...')
    const { expectedOutput } = await generateBaseSummaryAndOutput(
      existingEvents,
      baseTime,
      'MostViewedASingleViewPerProduct'
    )
    return { events: existingEvents, expectedOutput }
  }

  console.log('Generating new events...')
  const events = generateNewEvents(baseTime, options)
  const { expectedOutput } = await generateBaseSummaryAndOutput(
    events,
    baseTime,
    'MostViewedSingleViewPerProduct'
  )
  return { events, expectedOutput }
}

export const validateTestOutput = validateMostViewedTestOutput
