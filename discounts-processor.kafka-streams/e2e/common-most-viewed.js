import config from './config.js'
import {
  createProductViewEvent,
  createPagePingEvents,
  createSummaryFile,
  Validator,
  ValidationRules
} from './common.js'

export const createWebpageId = productId => `page${productId.replace('product', '')}`

export const ALL_PRODUCTS = [
  { id: 'product1', name: 'SP Dunk Low Retro', price: 119.99 },
  { id: 'product2', name: 'SP Air Force 1 Shadow', price: 129.99 },
  { id: 'product3', name: 'Total Orange', price: 129.99 },
  { id: 'product4', name: 'SP Air Max Plus 3', price: 189.99 },
  { id: 'product5', name: 'SP Flex Runner 2', price: 42.99 },
  { id: 'product6', name: 'SP React Vision', price: 159.99 },
  { id: 'product7', name: 'SP Zoom Pegasus', price: 139.99 },
  { id: 'product8', name: 'SP Metcon 8', price: 149.99 },
  { id: 'product9', name: 'SP Free Run 5.0', price: 109.99 },
  { id: 'product10', name: 'SP Air Zoom Structure', price: 169.99 }
]

export async function generateBaseSummaryAndOutput(events, baseTime, testName) {
  const summary = await createSummaryFile(events, testName)
  const mostViewedProduct = summary.products[0]
  return {
    expectedOutput: {
      discount: {
        rate: 0.1,
        by_number_of_views: {
          views: mostViewedProduct.views,
          duration_in_seconds: mostViewedProduct.durationInSeconds
        }
      },
      user_id: config.USER_ID,
      product_id: mostViewedProduct.productId,
      generated_at: baseTime.toISOString()
    }
  }
}

export function generateProductViewEvents(baseTime, options, products) {
  let events = []
  let currentTime = new Date(baseTime)
  const { maxPings = 7 } = options
  let lastProductViewTime = null

  for (const product of products) {
    // Apply random delay before generating the next product_view
    if (lastProductViewTime) {
      const randomDelay =
        Math.floor(
          Math.random() *
            (config.MAX_DELAY_SECONDS_TO_NEXT_PRODUCT -
              config.MIN_DELAY_SECONDS_TO_NEXT_PRODUCT +
              1)
        ) + config.MIN_DELAY_SECONDS_TO_NEXT_PRODUCT

      currentTime = new Date(
        Math.max(
          currentTime.getTime() + randomDelay * 1000,
          lastProductViewTime.getTime() + config.MIN_DELAY_SECONDS_TO_NEXT_PRODUCT * 1000
        )
      )
    }

    const webpageId = createWebpageId(product.id)

    events.push(
      createProductViewEvent({
        timestamp: currentTime,
        userId: config.USER_ID,
        webpageId,
        productId: product.id,
        productName: product.name,
        productPrice: product.price
      })
    )

    lastProductViewTime = currentTime

    const shouldHavePings = Math.random() > config.NO_PINGS_PROBABILITY
    const pingCount = shouldHavePings ? Math.floor(Math.random() * maxPings) + 1 : 0

    if (pingCount > 0) {
      const { events: pingEvents, lastTimestamp } = createPagePingEvents(
        currentTime,
        config.USER_ID,
        webpageId,
        pingCount
      )
      events = [...events, ...pingEvents]
      currentTime = lastTimestamp
    }
  }

  return { events, lastTimestamp: currentTime }
}

export function validateMostViewedTestOutput(outputValue, expectedOutput) {
  const { ISOString } = ValidationRules
  return new Validator(outputValue, expectedOutput)
    .validate('user_id')
    .validate('product_id')
    .validate('discount.rate')
    .validate('discount.by_number_of_views.views')
    .validate('discount.by_number_of_views.duration_in_seconds')
    .validate(ISOString('generated_at'))
    .result()
}
