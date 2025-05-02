import config from '../config.js'
import {
  createProductViewEvent,
  createPagePingEvents,
  Validator,
  ValidationRules
} from '../common.js'

export async function createEvents(baseTime) {
  const WEBPAGE_ID = 'page1'
  const DISCOUNT_PRODUCT_ID = 'product1'
  const DISCOUNT_PING_COUNT = config.MIN_PINGS_FOR_DISCOUNT
  const events = []

  events.push(
    createProductViewEvent({
      timestamp: baseTime,
      userId: config.USER_ID,
      webpageId: WEBPAGE_ID,
      productId: DISCOUNT_PRODUCT_ID,
      productName: 'Product 1',
      productPrice: 99.99
    })
  )

  createPagePingEvents(baseTime, config.USER_ID, WEBPAGE_ID, DISCOUNT_PING_COUNT).events.forEach(
    event => {
      events.push(event)
    }
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
