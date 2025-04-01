import { loadConfig } from './load-config.js'
import { createLogger } from './logger.js'
import { IntervalTracker } from './interval-tracker.js'
import {
  createKafkaClient,
  simulateFrequentViewKafka,
  simulateLongViewKafka,
  simulateNormalViewKafka,
} from './kafka-simulators.js'
import {
  createSnowplowTracker,
  simulateFrequentView,
  simulateLongView,
  simulateNormalView,
} from './snowplow-simulators.js'

const config = await loadConfig()
const logger = createLogger(config.logging)

const intervalTracker = new IntervalTracker(
  logger,
  config.simulation.cycle.duration,
  config.simulation.cycle.warningInterval
)

function showUsage(): void {
  logger.info(`
Usage: node index.js <behavior> [mode]

Available behaviors:
  frequent  - Simulates frequent views of the same product
  long      - Simulates a long duration product view
  normal    - Simulates normal browsing behavior

Available modes:
  snowplow  - Sends events to Snowplow collector (default)
  kafka     - Sends events to Kafka topic

Example: 
  node index.js frequent
  node index.js frequent kafka
`)
  process.exit(1)
}

async function simulateUserBehavior(behavior: string, mode: string = 'snowplow'): Promise<void> {
  let kafka
  let producer
  let snowplowTracker

  try {
    if (mode === 'kafka') {
      kafka = createKafkaClient(config)
      producer = kafka.producer()
      try {
        await producer.connect()
        logger.info('Connected to Kafka')
      } catch (error) {
        logger.error('Kafka is not available')
        process.exit(1)
      }
    } else {
      snowplowTracker = createSnowplowTracker(config)
    }

    switch (behavior) {
      case 'frequent':
        await (mode === 'kafka'
          ? simulateFrequentViewKafka(producer!, config, logger, intervalTracker)
          : simulateFrequentView(snowplowTracker!, config, logger, intervalTracker))
        break
      case 'long':
        await (mode === 'kafka'
          ? simulateLongViewKafka(producer!, config, logger, intervalTracker)
          : simulateLongView(snowplowTracker!, config, logger))
        break
      case 'normal':
        await (mode === 'kafka'
          ? simulateNormalViewKafka(producer!, config, logger)
          : simulateNormalView(snowplowTracker!, config, logger))
        break
      default:
        showUsage()
    }
  } catch (error) {
    logger.error(`Error during simulation: ${error}`)
    if (producer) {
      await producer.disconnect()
    }
    process.exit(1)
  }
}

process.on('SIGINT', async () => {
  logger.info('Gracefully shutting down...')
  intervalTracker.stop()
  process.exit(0)
})

process.on('unhandledRejection', error => {
  logger.error(`Unhandled rejection: ${error instanceof Error ? error.message : String(error)}`)
  process.exit(1)
})

const behavior = process.argv[2]
const mode = process.argv[3] || 'snowplow'

if (!behavior) {
  showUsage()
} else {
  simulateUserBehavior(behavior, mode).catch(error => {
    logger.error(
      `Error during simulation: ${error instanceof Error ? error.message : String(error)}`
    )
    process.exit(1)
  })
}
