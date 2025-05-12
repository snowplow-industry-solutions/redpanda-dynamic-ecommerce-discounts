import {
  ALL_PRODUCTS,
  generateBaseSummaryAndOutput,
  generateProductViewEvents,
  validateMostViewedTestOutput
} from '../common-most-viewed.js'

function generateNewEvents(baseTime, options) {
  const { maxProducts = 10, maxViewsPerProduct = 5, minViewsPerProduct = 0 } = options

  const productSequence = []
  const numProducts = Math.min(maxProducts, ALL_PRODUCTS.length)
  const availableProducts = [...ALL_PRODUCTS].sort(() => Math.random() - 0.5).slice(0, numProducts)

  availableProducts.forEach(product => {
    const viewCount =
      Math.floor(Math.random() * (maxViewsPerProduct - minViewsPerProduct + 1)) + minViewsPerProduct
    for (let i = 0; i < viewCount; i++) {
      productSequence.push(product)
    }
  })

  const finalSequence = []
  const tempSequence = [...productSequence]

  while (tempSequence.length > 0) {
    const availableIndices = tempSequence.reduce((acc, product, index) => {
      const lastProduct = finalSequence[finalSequence.length - 1]
      if (!lastProduct || product.id !== lastProduct.id) {
        acc.push(index)
      }
      return acc
    }, [])

    const selectedIndex =
      availableIndices.length > 0
        ? availableIndices[Math.floor(Math.random() * availableIndices.length)]
        : 0

    finalSequence.push(tempSequence[selectedIndex])
    tempSequence.splice(selectedIndex, 1)
  }

  const { events: generatedEvents } = generateProductViewEvents(baseTime, options, finalSequence)
  return generatedEvents.sort((a, b) => new Date(a.collector_tstamp) - new Date(b.collector_tstamp))
}

export async function createEvents(baseTime, options, existingEvents = null) {
  if (existingEvents) {
    console.log('Using existing events to regenerate expected output...')
    const { expectedOutput } = await generateBaseSummaryAndOutput(
      existingEvents,
      baseTime,
      'MostViewedMultipleViewsPerProduct'
    )
    return { events: existingEvents, expectedOutput }
  }

  console.log('Generating new events...')
  const events = generateNewEvents(baseTime, options)
  const { expectedOutput } = await generateBaseSummaryAndOutput(
    events,
    baseTime,
    'MostViewedMultipleViewsPerProduct'
  )
  return { events, expectedOutput }
}

export const validateTestOutput = validateMostViewedTestOutput
