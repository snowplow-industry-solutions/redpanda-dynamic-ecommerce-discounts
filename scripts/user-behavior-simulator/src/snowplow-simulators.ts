import nodeTrackerPkg from '@snowplow/node-tracker'
import { Config, Product, Logger, IntervalTracker } from './types'

const { tracker, gotEmitter, HttpProtocol, HttpMethod, buildSelfDescribingEvent } = nodeTrackerPkg

type SnowplowTracker = ReturnType<typeof tracker>

export function createSnowplowTracker(config: Config): SnowplowTracker {
  const emitter = gotEmitter(
    config.snowplow.endpoint,
    HttpProtocol.HTTP,
    config.snowplow.port,
    HttpMethod.POST,
    1
  )

  return tracker(emitter, 'sp', 'user-behavior-simulator', false)
}

const createProductContext = (product: Product) => ({
  schema: 'iglu:com.snowplowanalytics.snowplow.ecommerce/product/jsonschema/1-0-0',
  data: {
    id: product.id,
    name: product.name,
    price: product.price,
  },
})

const sleep = (ms: number): Promise<void> => new Promise(resolve => setTimeout(resolve, ms))

export async function simulateFrequentView(
  snowplowTracker: SnowplowTracker,
  config: Config,
  logger: Logger,
  intervalTracker?: IntervalTracker
): Promise<void> {
  const frequentViewer = config.mocks.users[0]
  const targetProduct = config.mocks.products[0]

  logger.info(
    `Simulating user ${frequentViewer.name} viewing product "${targetProduct.name}" multiple times...`
  )

  if (intervalTracker) intervalTracker.start()

  for (let i = 0; i < 4; i++) {
    await trackProductView(snowplowTracker, frequentViewer.id, targetProduct, 20)
    logger.info(`Sent view event ${i + 1}`)
    await sleep(2000)
  }

  if (intervalTracker) intervalTracker.stop()
}

export async function simulateLongView(
  snowplowTracker: SnowplowTracker,
  config: Config,
  logger: Logger
): Promise<void> {
  const longViewer = config.mocks.users[1]
  const longViewProduct = config.mocks.products[1]

  logger.info(
    `Simulating user ${longViewer.name} with long view of product "${longViewProduct.name}"...`
  )

  await trackProductView(snowplowTracker, longViewer.id, longViewProduct, 95)
  logger.info('Sent long view event')
}

export async function simulateNormalView(
  snowplowTracker: SnowplowTracker,
  config: Config,
  logger: Logger
): Promise<void> {
  const normalViewer = config.mocks.users[2]

  logger.info(`Simulating user ${normalViewer.name} with normal views...`)

  for (const product of config.mocks.products.slice(0, 2)) {
    await trackProductView(snowplowTracker, normalViewer.id, product, 15)
    logger.info(`Sent normal view event for "${product.name}"`)
    await sleep(1000)
  }
}

async function trackProductView(
  snowplowTracker: SnowplowTracker,
  userId: string,
  product: Product,
  viewDuration: number
): Promise<void> {
  const event = buildSelfDescribingEvent({
    event: {
      schema: 'iglu:com.snowplowanalytics.snowplow.ecommerce/product_view/jsonschema/1-0-0',
      data: {
        userId,
        productId: product.id,
        viewDuration,
      },
    },
  })

  snowplowTracker.track(event, [createProductContext(product)])
}
