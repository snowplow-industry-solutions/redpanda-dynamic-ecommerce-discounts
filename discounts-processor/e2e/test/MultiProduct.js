import config from '../config.js'
import {
  createProductViewEvent,
  createPagePingEvents,
  Validator,
  ValidationRules
} from '../common.js'

export async function createEvents(baseTime) {
  const DISCOUNT_PRODUCT_ID = 'product3'
  const DISCOUNT_PING_COUNT = 12

  const createProductEvents = (
    webpageId,
    productId,
    productName,
    price,
    pingCount,
    previousEvents
  ) => {
    let startTime
    if (previousEvents.length === 0) {
      startTime = new Date(baseTime)
    } else {
      const lastEvent = previousEvents[previousEvents.length - 1]
      startTime = new Date(lastEvent.collector_tstamp)
      startTime.setSeconds(startTime.getSeconds() + config.DELAY_SECONDS_BETWEEN_PINGS)
    }

    const productView = createProductViewEvent({
      timestamp: startTime,
      userId: config.USER_ID,
      webpageId,
      productId,
      productName,
      productPrice: price
    })

    const shouldHavePings = Math.random() > config.NO_PINGS_PROBABILITY
    const actualPingCount =
      productId === DISCOUNT_PRODUCT_ID ? DISCOUNT_PING_COUNT : shouldHavePings ? pingCount : 0

    const pings =
      actualPingCount > 0
        ? createPagePingEvents(startTime, config.USER_ID, webpageId, actualPingCount).events
        : []

    return [productView, ...pings]
  }

  const productConfigs = [
    ['page1', 'product1', 'Product 1', 99.99, 9],
    ['page2', 'product2', 'Product 2', 149.99, 10],
    ['page3', DISCOUNT_PRODUCT_ID, 'Product 3', 199.99, DISCOUNT_PING_COUNT]
  ]

  const events = productConfigs.reduce(
    (acc, [webpageId, productId, productName, price, pingCount]) => [
      ...acc,
      ...createProductEvents(webpageId, productId, productName, price, pingCount, acc)
    ],
    []
  )

  const expectedOutput = {
    discount: {
      rate: 0.1,
      by_view_time: {
        duration_in_seconds: config.durationInSeconds(DISCOUNT_PING_COUNT)
      }
    },
    user_id: config.USER_ID,
    product_id: DISCOUNT_PRODUCT_ID,
    generated_at: baseTime.toISOString()
  }

  return { events, expectedOutput }
}

export function validateTestOutput(outputValue, expectedOutput) {
  const { ISOString } = ValidationRules
  return new Validator(outputValue, expectedOutput)
    .validate('user_id')
    .validate('product_id')
    .validate('discount.rate')
    .validate('discount.by_view_time.duration_in_seconds')
    .validate(ISOString('generated_at'))
    .result()
}
