import { loadConfig } from './load-config.js';
import { IntervalTracker } from './interval-tracker.js';
import { 
  createKafkaClient, 
  simulateFrequentViewKafka, 
  simulateLongViewKafka, 
  simulateNormalViewKafka 
} from './kafka-simulators.js';
import {
  createSnowplowTracker,
  simulateFrequentView,
  simulateLongView,
  simulateNormalView
} from './snowplow-simulators.js';

const config = await loadConfig();

const logger = {
  debug: (message) => {
    if (process.env.DEBUG) {
      const timestamp = new Date().toISOString();
      console.log(`[${timestamp}] DEBUG: ${message}`);
    }
  },
  info: (message) => {
    const timestamp = new Date().toISOString();
    console.log(`[${timestamp}] INFO: ${message}`);
  },
  error: (message) => {
    const timestamp = new Date().toISOString();
    console.error(`[${timestamp}] ERROR: ${message}`);
  }
};

const intervalTracker = new IntervalTracker(logger, config.simulation.cycleDuration, config.simulation.warningInCycleInterval);

function showUsage() {
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
`);
  process.exit(1);
}

async function simulateUserBehavior(behavior, mode = 'snowplow') {
  let kafka;
  let producer;
  let snowplowTracker;

  try {
    if (mode === 'kafka') {
      kafka = createKafkaClient(config, logger);
      producer = kafka.producer();
      try {
        await producer.connect();
        logger.info('Connected to Kafka');
      } catch (error) {
        logger.error('Kafka is not available');
        process.exit(1);
      }
    } else {
      snowplowTracker = createSnowplowTracker(config);
    }

    switch (behavior) {
      case 'frequent':
        await (mode === 'kafka' 
          ? simulateFrequentViewKafka(producer, config, logger, intervalTracker) 
          : simulateFrequentView(snowplowTracker, config, logger, intervalTracker));
        break;
      case 'long':
        await (mode === 'kafka' 
          ? simulateLongViewKafka(producer, config, logger) 
          : simulateLongView(snowplowTracker, config, logger));
        break;
      case 'normal':
        await (mode === 'kafka' 
          ? simulateNormalViewKafka(producer, config, logger) 
          : simulateNormalView(snowplowTracker, config, logger));
        break;
      default:
        showUsage();
    }
  } catch (error) {
    logger.error(`Error during simulation: ${error}`);
    if (producer) {
      await producer.disconnect();
    }
    process.exit(1);
  }
}

process.on('SIGINT', async () => {
  logger.info('Gracefully shutting down...');
  intervalTracker.stop();
  process.exit(0);
});

const behavior = process.argv[2];
const mode = process.argv[3] || 'snowplow';

if (!behavior) {
  showUsage();
} else {
  simulateUserBehavior(behavior, mode);
}
