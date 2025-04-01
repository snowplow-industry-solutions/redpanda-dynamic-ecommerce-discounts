import { Kafka, Producer } from 'kafkajs'
import { Config, Product, Logger, IntervalTracker } from './types'

interface ProductStats {
  views: number
  totalDuration: number
}

const productStats = new Map<string, ProductStats>()

export function createKafkaClient(config: Config): Kafka {
  return new Kafka({
    clientId: 'user-behavior-simulator',
    brokers: config.kafka.brokers,
  })
}

const sleepSeconds = (seconds: number): Promise<void> =>
  new Promise(resolve => setTimeout(resolve, seconds * 1000))

const getRandomProduct = (products: Product[]): Product =>
  products[Math.floor(Math.random() * products.length)]

async function sendKafkaEvent(
  producer: Producer,
  config: Config,
  product: Product,
  isPing: boolean = false
): Promise<void> {
  const event = {
    timestamp: new Date().toISOString(),
    type: isPing ? 'product_view_ping' : 'product_view',
    userId: config.mocks.users[0].id,
    productId: product.id,
    productName: product.name,
    price: product.price,
  }

  await producer.send({
    topic: config.kafka.topic,
    messages: [{ value: JSON.stringify(event) }],
  })

  const stats = productStats.get(product.id) || { views: 0, totalDuration: 0 }
  stats.views += 1
  stats.totalDuration += isPing ? 10 : 0
  productStats.set(product.id, stats)
}

async function sendEventAndWait(
  producer: Producer,
  config: Config,
  logger: Logger,
  product: Product,
  isPing: boolean,
  currentProduct: Product | null
): Promise<Product> {
  if (!isPing || currentProduct?.id !== product.id) {
    await sendKafkaEvent(producer, config, product, false)
    logger.info(`Sent ${isPing ? 'ping' : 'view'} event for product "${product.name}"`)
    return product
  }
  await sendKafkaEvent(producer, config, product, true)
  return currentProduct
}

export async function simulateFrequentViewKafka(
  producer: Producer,
  config: Config,
  logger: Logger,
  intervalTracker: IntervalTracker
): Promise<void> {
  const findBestProduct = (
    products: Product[],
    stats: Map<string, ProductStats>
  ): Product | null => {
    let bestProduct: Product | null = null
    let maxViews = 0
    let maxDuration = 0

    for (const product of products) {
      const productStats = stats.get(product.id)
      if (
        productStats &&
        (productStats.views > maxViews ||
          (productStats.views === maxViews && productStats.totalDuration > maxDuration))
      ) {
        maxViews = productStats.views
        maxDuration = productStats.totalDuration
        bestProduct = product
      }
    }

    return bestProduct
  }

  intervalTracker.setOnCycleEnd(() => {
    logger.info('Cycle completed')
    const productStats = intervalTracker.getCycleData('productStats') as Map<string, ProductStats>

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
        `Cycle completed - DISCOUNT WINNER: "${bestProduct.name}" with ${
          stats!.views
        } views and ${stats!.totalDuration}s total duration`
      )
    } else {
      logger.debug('No product qualified for discount in this cycle')
    }
  })

  intervalTracker.setCycleData('productStats', new Map<string, ProductStats>())

  while (true) {
    try {
      if (intervalTracker.isNewCycle()) {
        intervalTracker.setCycleData('productStats', new Map<string, ProductStats>())
      }

      const productStats = intervalTracker.getCycleData('productStats')
      if (!productStats) {
        intervalTracker.setCycleData('productStats', new Map<string, ProductStats>())
        continue
      }

      const product = getRandomProduct(config.mocks.products)

      const minDuration = config.simulation.frequentView.minDuration
      const maxDuration = config.simulation.frequentView.maxDuration
      const viewDuration = Math.floor(Math.random() * (maxDuration - minDuration + 1)) + minDuration

      const currentStats = (productStats as Map<string, ProductStats>).get(product.id) || {
        views: 0,
        totalDuration: 0,
      }
      ;(productStats as Map<string, ProductStats>).set(product.id, {
        views: currentStats.views + 1,
        totalDuration: currentStats.totalDuration + viewDuration,
      })

      await sendKafkaEvent(producer, config, product, false)

      const statsLog = Array.from((productStats as Map<string, ProductStats>).entries())
        .map(([productId, stats]) => {
          const product = config.mocks.products.find(p => p.id === productId)
          return `"${product?.name}": ${stats.views} views, ${stats.totalDuration}s total`
        })
        .join(', ')

      const progress = intervalTracker.getCycleProgress()
      logger.info(
        `Cycle progress: ${Math.floor(progress.percentComplete)}% - Current stats - ${statsLog}`
      )

      await sleepSeconds(viewDuration)
    } catch (error) {
      logger.error(
        `Error in frequent view simulation: ${
          error instanceof Error ? error.message : String(error)
        }`
      )
      intervalTracker.setCycleData('productStats', new Map<string, ProductStats>())
    }
  }
}

export async function simulateLongViewKafka(
  producer: Producer,
  config: Config,
  logger: Logger,
  intervalTracker: IntervalTracker
): Promise<void> {
  let currentProduct: Product | null = null

  while (true) {
    try {
      const product = getRandomProduct(config.mocks.products)
      logger.info(`Starting new long view simulation for product "${product.name}"`)

      const startTime = Date.now()
      let eventCount = 0

      currentProduct = await sendEventAndWait(
        producer,
        config,
        logger,
        product,
        false,
        currentProduct
      )

      const longViewDurationMs = config.simulation.longView.duration * 1000

      while (Date.now() - startTime < longViewDurationMs) {
        if (intervalTracker.isNewCycle()) {
          // The IntervalTracker already logs cycle start messages
        }
        currentProduct = await sendEventAndWait(
          producer,
          config,
          logger,
          product,
          true,
          currentProduct
        )
        eventCount++
        logger.info(`Duration: ${Math.floor((Date.now() - startTime) / 1000)}s`)
        await sleepSeconds(config.simulation.longView.pagePingInterval)
      }

      logger.info(
        `Completed long view simulation with ${eventCount} events over ${Math.floor(
          (Date.now() - startTime) / 1000
        )} seconds`
      )
      await sleepSeconds(config.simulation.betweenLongViewInterval)
    } catch (error) {
      logger.error(
        `Error in long view simulation: ${error instanceof Error ? error.message : String(error)}`
      )
    }
  }
}

export async function simulateNormalViewKafka(
  producer: Producer,
  config: Config,
  logger: Logger
): Promise<void> {
  let currentProduct: Product | null = null

  while (true) {
    logger.info('Starting new normal viewing pattern simulation')

    const randomProduct = getRandomProduct(config.mocks.products)
    currentProduct = await sendEventAndWait(
      producer,
      config,
      logger,
      randomProduct,
      false,
      currentProduct
    )
    await sleepSeconds(config.simulation.betweenNormalViewInterval)
  }
}
