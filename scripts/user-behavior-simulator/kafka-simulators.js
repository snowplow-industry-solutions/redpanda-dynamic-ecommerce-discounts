import { Kafka, logLevel } from 'kafkajs';

export function createKafkaClient(config, logger) {
  const kafka = new Kafka({
    clientId: config.kafka.clientId,
    brokers: config.kafka.brokers,
    logLevel: logLevel.INFO,
    logCreator: () => {
      return ({ namespace, level, label, log }) => {
        const { message, timestamp } = log;
        const logLevel = label || level.toLocaleUpperCase();
        console.log(`[${timestamp}] ${logLevel}: ${message}`);
      };
    }
  });

  return kafka;
}

function generateJson(config, product, isPagePing = false) {
  const data = {
    event_name: isPagePing ? 'page_ping' : 'snowplow_ecommerce_action',
    collector_tstamp: new Date().toISOString(),
    user_id: config.simulation.fixedUserId,
    product_id: product.id,
    product_name: product.name,
    product_price: product.price,
    webpage_id: product.webpage_id
  };

  if (isPagePing) {
    const { product_id, product_name, product_price, ...newData } = data;
    return newData;
  }

  return data;
}

async function sendKafkaEvent(producer, config, product, isPagePing = false) {
  const event = generateJson(config, product, isPagePing);
  await producer.send({
    topic: config.kafka.topic,
    messages: [{ 
      key: config.simulation.fixedUserId,
      value: JSON.stringify(event) 
    }]
  });
  return event;
}

const sleepSeconds = (seconds) => new Promise(resolve => setTimeout(resolve, seconds * 1000));

const getRandomProduct = (products) => {
  return products[Math.floor(Math.random() * products.length)];
};

async function sendEventAndWait(producer, config, logger, product, isPagePing, currentProduct) {
  if (isPagePing || currentProduct?.id !== product.id) {
    await sendKafkaEvent(producer, config, product, isPagePing);
    logger.info(`Sent ${isPagePing ? 'page_ping' : 'snowplow_ecommerce_action'} event for "${product.name}"`);
    
    const waitSeconds = isPagePing 
      ? config.simulation.pagePingInterval 
      : config.simulation.afterActionEventInterval;
    
    await sleepSeconds(waitSeconds);
    return isPagePing ? currentProduct : product;
  }
  return currentProduct;
}

export async function simulateFrequentViewKafka(producer, config, logger, intervalTracker) {
  intervalTracker.setCycleData('productStats', new Map());
  
  while (true) {
    try {
      if (intervalTracker.isNewCycle()) {
        const productStats = intervalTracker.getCycleData('productStats');
        
        logger.debug('End of cycle stats:');
        for (const [productId, stats] of productStats.entries()) {
          const product = config.mocks.products.find(p => p.id === productId);
          logger.debug(`  Product: ${product?.name}, Views: ${stats.views}, Duration: ${stats.totalDuration}s`);
        }

        let bestProduct = null;
        let maxViews = 0;
        let maxDuration = 0;

        for (const [productId, stats] of productStats.entries()) {
          logger.debug(`Checking product ${productId} with ${stats.views} views`);
          if (stats.views > maxViews || (stats.views === maxViews && stats.totalDuration > maxDuration)) {
            const product = config.mocks.products.find(p => p.id === productId);
            if (product) {
              bestProduct = product;
              maxViews = stats.views;
              maxDuration = stats.totalDuration;
              logger.debug(`New best: ${product.name} with ${stats.views} views`);
            }
          }
        }

        if (bestProduct) {
          const stats = productStats.get(bestProduct.id);
          logger.info(`Cycle completed - DISCOUNT WINNER: "${bestProduct.name}" with ${stats.views} views and ${stats.totalDuration}s total duration`);
        } else {
          logger.debug('No product qualified for discount in this cycle');
        }

        intervalTracker.setCycleData('productStats', new Map());
      }

      const productStats = intervalTracker.getCycleData('productStats');
      if (!productStats) {
        intervalTracker.setCycleData('productStats', new Map());
        continue;
      }
      
      const product = getRandomProduct(config.mocks.products);
      
      const minDuration = config.simulation.frequentView.minViewDuration;
      const maxDuration = config.simulation.frequentView.maxViewDuration;
      const viewDuration = Math.floor(Math.random() * (maxDuration - minDuration + 1)) + minDuration;
      
      const currentStats = productStats.get(product.id) || { views: 0, totalDuration: 0 };
      productStats.set(product.id, {
        views: currentStats.views + 1,
        totalDuration: currentStats.totalDuration + viewDuration
      });
      
      await sendKafkaEvent(producer, config, product, false);
      
      const statsLog = Array.from(productStats.entries())
        .map(([productId, stats]) => {
          const product = config.mocks.products.find(p => p.id === productId);
          return `"${product.name}": ${stats.views} views, ${stats.totalDuration}s total`;
        })
        .join(', ');
      
      const progress = intervalTracker.getCycleProgress();
      logger.info(`Cycle progress: ${Math.floor(progress.percentComplete)}% - Current stats - ${statsLog}`);
      
      await sleepSeconds(viewDuration);
      
      if (config.simulation.betweenFrequencyViewInterval > 0) {
        await sleepSeconds(config.simulation.betweenFrequencyViewInterval);
      }
    } catch (error) {
      logger.error(`Error in frequent view simulation: ${error.message}`);
      intervalTracker.setCycleData('productStats', new Map());
    }
  }
}

export async function simulateLongViewKafka(producer, config, logger) {
  let currentProduct = null;
  
  while (true) {
    const product = getRandomProduct(config.mocks.products);
    logger.info(`Starting new long view simulation for product "${product.name}"`);
    
    const startTime = Date.now();
    let eventCount = 0;
    
    currentProduct = await sendEventAndWait(producer, config, logger, product, false, currentProduct);
    
    const longViewDurationMs = config.simulation.longViewInterval * 1000;
    
    while (Date.now() - startTime < longViewDurationMs) {
      currentProduct = await sendEventAndWait(producer, config, logger, product, true, currentProduct);
      eventCount++;
      logger.info(`Duration: ${Math.floor((Date.now() - startTime) / 1000)}s`);
    }
    
    logger.info(`Completed long view simulation with ${eventCount} events over ${Math.floor((Date.now() - startTime) / 1000)} seconds`);
    await sleepSeconds(config.simulation.betweenLongViewInterval);
  }
}

export async function simulateNormalViewKafka(producer, config, logger) {
  let currentProduct = null;
  
  while (true) {
    logger.info('Starting new normal viewing pattern simulation');
    
    const randomProduct = getRandomProduct(config.mocks.products);
    currentProduct = await sendEventAndWait(producer, config, logger, randomProduct, false, currentProduct);
    await sleepSeconds(config.simulation.betweenNormalViewInterval);
  }
}
