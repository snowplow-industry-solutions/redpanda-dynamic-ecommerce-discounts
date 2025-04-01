import nodeTrackerPkg from '@snowplow/node-tracker';

const { tracker, gotEmitter, HttpProtocol, HttpMethod, buildSelfDescribingEvent } = nodeTrackerPkg;

export function createSnowplowTracker(config) {
  const emitter = gotEmitter(
    config.snowplow.endpoint,
    HttpProtocol.HTTP,
    config.snowplow.port,
    HttpMethod.POST,
    1
  );

  return tracker(
    emitter,
    'sp',
    'user-behavior-simulator',
    false
  );
}

const createProductContext = (product) => ({
  schema: 'iglu:com.snowplowanalytics.snowplow.ecommerce/product/jsonschema/1-0-0',
  data: {
    id: product.id,
    name: product.name,
    price: product.price,
    currency: product.currency,
    category: product.category
  }
});

const trackProductView = (snowplowTracker, userId, product, viewDuration) => {
  snowplowTracker.setUserId(userId);

  const event = buildSelfDescribingEvent({
    event: {
      schema: 'iglu:com.snowplowanalytics.snowplow.ecommerce/product_view/jsonschema/1-0-0',
      data: {
        page_view_duration: viewDuration
      }
    }
  });

  return new Promise((resolve, reject) => {
    snowplowTracker.track(
      event,
      [createProductContext(product)],
      (err) => {
        if (err) {
          reject(err);
        } else {
          resolve();
        }
      }
    );
  });
};

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

export async function simulateFrequentView(snowplowTracker, config, logger) {
  const frequentViewer = config.mocks.users[0];
  const targetProduct = config.mocks.products[0];
  
  logger.info(`Simulating user ${frequentViewer} viewing product "${targetProduct.name}" multiple times...`);
  
  for (let i = 0; i < 4; i++) {
    await trackProductView(snowplowTracker, frequentViewer, targetProduct, 20);
    logger.info(`Sent view event ${i + 1}`);
    await sleep(2000);
  }
}

export async function simulateLongView(snowplowTracker, config, logger) {
  const longViewer = config.mocks.users[1];
  const longViewProduct = config.mocks.products[1];
  
  logger.info(`Simulating user ${longViewer} with long view of product "${longViewProduct.name}"...`);
  
  await trackProductView(snowplowTracker, longViewer, longViewProduct, 95);
  logger.info('Sent long view event');
}

export async function simulateNormalView(snowplowTracker, config, logger) {
  const normalViewer = config.mocks.users[2];
  
  logger.info(`Simulating user ${normalViewer} with normal views...`);
  
  for (const product of config.mocks.products.slice(0, 2)) {
    await trackProductView(snowplowTracker, normalViewer, product, 15);
    logger.info(`Sent normal view event for "${product.name}"`);
    await sleep(1000);
  }
}