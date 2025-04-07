import fs from 'fs'
import path from 'path'
import { Kafka, Producer } from 'kafkajs'
import { Config, Product, Logger, IntervalTracker } from './types'
import { ProductStats, findBestProduct } from './product-analytics'
import { getRandomProduct, updateProductStats, sleepSeconds } from './product-utils'

type SimulationType = 'frequent' | 'long' | 'normal'
interface Event {
  collector_tstamp: string
  event_name: 'page_ping' | 'snowplow_ecommerce_action'
  user_id: string
  product_id?: string
  product_name?: string
  product_price?: number
  webpage_id: string
}

class JsonlSink {
  private stream: fs.WriteStream

  constructor(filePath: string) {
    const normalizedPath = path.resolve(filePath)
    const dir = path.dirname(normalizedPath)
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true })
    }
    this.stream = fs.createWriteStream(normalizedPath, { flags: 'a' })
  }

  async send(message: string): Promise<void> {
    return new Promise((resolve, reject) => {
      this.stream.write(message + '\n', (error: any) => {
        if (error) reject(error)
        else resolve()
      })
    })
  }

  async close(): Promise<void> {
    return new Promise(resolve => {
      this.stream.end(() => resolve())
    })
  }
}

let jsonlSink: JsonlSink | null = null

export function createKafkaClient(config: Config): Kafka {
  return new Kafka({
    clientId: 'user-behavior-simulator',
    brokers: config.kafka.brokers,
  })
}

async function sendEvent(
  producer: Producer | null,
  config: Config,
  product: Product,
  isPing: boolean = false,
  simulationType: SimulationType,
  productStats?: Map<string, ProductStats>
): Promise<void> {
  let event: Event = {
    collector_tstamp: new Date().toISOString(),
    event_name: isPing ? 'page_ping' : 'snowplow_ecommerce_action',
    user_id: config.mocks.users[0].id,
    webpage_id: product.webpage_id,
  }

  if (!isPing) {
    event.product_id = product.id
    event.product_name = product.name
    event.product_price = product.price
  }

  const message = JSON.stringify(event)

  if (config.sink_type === 'jsonl') {
    if (!jsonlSink) {
      const filePath = `data/${simulationType}.jsonl`
      jsonlSink = new JsonlSink(filePath)
    }
    await jsonlSink.send(message)
  } else if (producer) {
    await producer.send({
      topic: config.kafka.topic,
      messages: [
        {
          key: config.mocks.users[0].id,
          value: message,
        },
      ],
    })
  }

  if (productStats) {
    updateProductStats(product, isPing, productStats)
  }
}

export function generateStatsLog(
  productStats: Map<string, ProductStats>,
  products: Product[],
  progress: { percentComplete: number }
): string {
  const statsLog = Array.from(productStats.entries())
    .map(([productId, stats]) => {
      const product = products.find(p => p.id === productId)
      return `  "${product?.name}": ${stats.views} views, ${stats.totalDuration}s total`
    })
    .join('\n')

  return `\nCycle progress: ${Math.floor(progress.percentComplete)}% - Current stats:\n${statsLog}`
}

export async function simulateFrequentViewKafka(
  producer: Producer | null,
  config: Config,
  logger: Logger,
  intervalTracker: IntervalTracker
): Promise<void> {
  logger.info('Starting frequent view simulation...', 'simulateFrequentViewKafka')

  intervalTracker.setOnCycleEnd(() => {
    const productStats = intervalTracker.getCycleData('productStats') as Map<string, ProductStats>
    if (!productStats) return

    logger.debug('End of cycle stats:')
    for (const [productId, stats] of productStats.entries()) {
      const product = config.mocks.products.find(p => p.id === productId)
      logger.debug(
        `  Product: ${product?.name}, Views: ${stats.views}, Duration: ${stats.totalDuration}s`
      )
    }

    const bestProduct = findBestProduct(config.mocks.products, productStats)
    if (bestProduct) {
      const stats = productStats.get(bestProduct.id)
      logger.info(
        `DISCOUNT WINNER: "${bestProduct.name}" with ${stats!.views} views and ` +
          `${stats!.totalDuration}s total duration`
      )
    } else {
      logger.debug('No product qualified for discount in this cycle')
    }
  })

  const initialProductStats = new Map<string, ProductStats>()
  intervalTracker.setCycleData('productStats', initialProductStats)
  logger.info('Starting frequent view simulation...')

  while (true) {
    try {
      let productStats: Map<string, ProductStats>
      if (intervalTracker.isNewCycle()) {
        productStats = new Map<string, ProductStats>()
        intervalTracker.setCycleData('productStats', productStats)
      } else {
        productStats = intervalTracker.getCycleData('productStats') as Map<string, ProductStats>
        if (!productStats) {
          productStats = new Map<string, ProductStats>()
          intervalTracker.setCycleData('productStats', productStats)
        }
      }

      const product = getRandomProduct(config.mocks.products)
      const minDuration = config.simulation.frequentView.minDuration
      const maxDuration = config.simulation.frequentView.maxDuration
      const viewDuration = Math.floor(Math.random() * (maxDuration - minDuration + 1)) + minDuration

      const currentStats = productStats.get(product.id) || {
        views: 0,
        totalDuration: 0,
      }
      productStats.set(product.id, {
        views: currentStats.views + 1,
        totalDuration: currentStats.totalDuration + viewDuration,
      })

      await sendEvent(producer, config, product, false, 'frequent', productStats)

      if (productStats.size > 0) {
        const progress = intervalTracker.getCycleProgress()
        const statsMessage = generateStatsLog(productStats, config.mocks.products, progress)
        logger.info(statsMessage, 'simulateFrequentViewKafka')
      }

      await sleepSeconds(viewDuration)
    } catch (error) {
      logger.error(
        `Error in frequent view simulation: ${
          error instanceof Error ? error.message : String(error)
        }`,
        'simulateFrequentViewKafka'
      )
      const newProductStats = new Map<string, ProductStats>()
      intervalTracker.setCycleData('productStats', newProductStats)
      await sleepSeconds(1)
    }
  }
}

const sendEventByType = async (
  isPing: boolean,
  product: Product,
  producer: Producer | null,
  config: Config,
  eventCount?: number
) => {
  let message =
    (isPing
      ? `Sent page_ping event ${eventCount ? eventCount + 1 : 1}`
      : 'Sent snowplow_ecommerce_action event') + '.'
  const seconds = isPing
    ? config.simulation.longView.pagePingInterval
    : config.simulation.snowplowEcommerceActionInterval
  message = message + ` Waiting ${seconds} seconds...`

  await sendEvent(producer, config, product, isPing, 'long')
  await sleepSeconds(seconds, message)
}

export async function simulateLongViewKafka(
  producer: Producer | null,
  config: Config,
  logger: Logger,
  intervalTracker: IntervalTracker
): Promise<void> {
  let lastProduct: Product | null = null
  const longViewDurationMs = config.simulation.longView.duration * 1000

  while (true) {
    try {
      let product: Product
      do {
        product = getRandomProduct(config.mocks.products)
      } while (lastProduct && product.id === lastProduct.id)

      lastProduct = product
      logger.info(`Starting new long view simulation for product "${product.name}"`)

      await sendEventByType(false, product, producer, config)

      const startTime = Date.now()
      let eventCount = 0
      do {
        intervalTracker.checkIfNewCycle()
        await sendEventByType(true, product, producer, config, eventCount)
        eventCount++
      } while (Date.now() - startTime <= longViewDurationMs)

      logger.info(
        `Completed long view simulation with ${eventCount} events over ${Math.floor(
          (Date.now() - startTime) / 1000
        )} seconds`
      )
      await sleepSeconds(
        config.simulation.betweenLongViewInterval,
        `Waiting ${config.simulation.betweenLongViewInterval} seconds between long views`
      )
    } catch (error) {
      logger.error(
        `Error in long view simulation: ${error instanceof Error ? error.message : String(error)}`
      )
    }
  }
}

export async function simulateNormalViewKafka(
  producer: Producer | null,
  config: Config,
  logger: Logger
): Promise<void> {
  // TODO
}

export async function cleanup(): Promise<void> {
  if (jsonlSink) {
    await jsonlSink.close()
    jsonlSink = null
  }
}
