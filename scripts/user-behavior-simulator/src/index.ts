import { loadConfig } from './load-config'
import { createLogger } from './logger'
import { IntervalTracker } from './interval-tracker'
import {
  createKafkaClient,
  simulateFrequentViewKafka,
  simulateLongViewKafka,
  simulateNormalViewKafka,
} from './kafka-simulators'
import { Producer } from 'kafkajs'

const { config, usedConfigFile } = await loadConfig()
const logger = createLogger(config.logging)

logger.info(`Using configuration from: ${usedConfigFile}`)

const intervalTracker = new IntervalTracker(
  logger,
  config.simulation.cycle.duration,
  config.simulation.cycle.warningInterval
)
intervalTracker.start()

function showUsage(): void {
  logger.info(`
Usage: node index.js <behavior>

Available behaviors:
  frequent  - Simulates frequent views of the same product
  long      - Simulates a long duration product view
  normal    - Simulates normal browsing behavior

Example: 
  node index.js frequent
  node index.js long
`)
  process.exit(1)
}

let kafka
let producer: Producer

async function simulateUserBehavior(behavior: string): Promise<void> {
  try {
    kafka = createKafkaClient(config)
    producer = kafka.producer()
    try {
      await producer.connect()
      logger.info('Connected to Kafka')
    } catch (error) {
      logger.error('Kafka is not available')
      process.exit(1)
    }

    switch (behavior) {
      case 'frequent':
        await simulateFrequentViewKafka(producer, config, logger, intervalTracker)
        break
      case 'long':
        await simulateLongViewKafka(producer, config, logger, intervalTracker)
        break
      case 'normal':
        await simulateNormalViewKafka(producer, config, logger)
        break
      default:
        showUsage()
    }
  } catch (error) {
    logger.error(
      `Error during simulation: ${error instanceof Error ? error.message : String(error)}`
    )
    if (producer) {
      await producer.disconnect()
    }
    process.exit(1)
  }
}

let isShuttingDown = false

process.on('SIGINT', async () => {
  if (isShuttingDown) return
  isShuttingDown = true

  try {
    logger.info('Gracefully shutting down...')
    intervalTracker.stop()
    if (producer) {
      await producer.disconnect()
    }
  } catch (error) {
    // Silently handle any errors during shutdown
  } finally {
    process.exit(0)
  }
})

process.on('unhandledRejection', error => {
  logger.error(`Unhandled rejection: ${error instanceof Error ? error.message : String(error)}`)
  process.exit(1)
})

const behavior = process.argv[2]

if (!behavior) {
  showUsage()
} else {
  simulateUserBehavior(behavior).catch(error => {
    logger.error(
      `Error during simulation: ${error instanceof Error ? error.message : String(error)}`
    )
    process.exit(1)
  })
}
