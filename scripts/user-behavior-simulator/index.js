import nodeTrackerPkg from '@snowplow/node-tracker';
const { tracker, gotEmitter, HttpProtocol, HttpMethod, buildSelfDescribingEvent } = nodeTrackerPkg;

const emitter = gotEmitter(
  'localhost',
  HttpProtocol.HTTP,
  9090,
  HttpMethod.POST,
  1
);

const snowplowTracker = tracker(
  emitter,
  'sp',
  'user-behavior-simulator',
  false
);

const mockUsers = [
  'sion@oso.com',
  'paulo@oso.com',
  'alex@snowplowanalytics.com',
  'trent@snowplowanalytics.com',
  'lucas@snowplowanalytics.com',
];

const mockProducts = [
  { id: '1', name: 'SP Dunk Low Retro', price: 119.99, currency: 'USD', category: 'Shoes' },
  { id: '2', name: 'SP Air Max Plus 3', price: 189.99, currency: 'USD', category: 'Shoes' },
  { id: '3', name: 'Total Orange', price: 129.99, currency: 'USD', category: 'Shoes' },
  { id: '4', name: 'SP Air Force 1 Shadow', price: 129.99, currency: 'USD', category: 'Shoes' },
  { id: '5', name: 'SP Flex Runner 2', price: 42.99, currency: 'USD', category: 'Shoes' }
];

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

const trackProductView = (userId, product, viewDuration) => {
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

const simulateFrequentViews = async () => {
  const frequentViewer = mockUsers[0];
  const targetProduct = mockProducts[0];
  
  console.log(`\nSimulating user ${frequentViewer} viewing product ${targetProduct.name} multiple times...`);
  
  for (let i = 0; i < 4; i++) {
    await trackProductView(frequentViewer, targetProduct, 20);
    console.log(`Sent view event ${i + 1}`);
    await sleep(2000);
  }
};

const simulateLongView = async () => {
  const longViewer = mockUsers[1];
  const longViewProduct = mockProducts[1];
  
  console.log(`\nSimulating user ${longViewer} with long view of product ${longViewProduct.name}...`);
  
  await trackProductView(longViewer, longViewProduct, 95);
  console.log('Sent long view event');
};

const simulateNormalViews = async () => {
  const normalViewer = mockUsers[2];
  
  console.log(`\nSimulating user ${normalViewer} with normal views...`);
  
  for (const product of mockProducts.slice(0, 2)) {
    await trackProductView(normalViewer, product, 15);
    console.log(`Sent normal view event for ${product.name}`);
    await sleep(1000);
  }
};

const showUsage = () => {
  console.log(`
Usage: node index.js <behavior>

Available behaviors:
  frequent  - Simulates frequent views of the same product
  long      - Simulates a long duration product view
  normal    - Simulates normal browsing behavior
  all       - Runs all simulations in sequence

Example: node index.js frequent
`);
  process.exit(1);
};

const simulateUserBehavior = async (behavior) => {
  try {
    switch (behavior) {
      case 'frequent':
        await simulateFrequentViews();
        break;
      case 'long':
        await simulateLongView();
        break;
      case 'normal':
        await simulateNormalViews();
        break;
      case 'all':
        await simulateFrequentViews();
        await simulateLongView();
        await simulateNormalViews();
        break;
      default:
        showUsage();
    }
    console.log('\nSimulation completed!');
  } catch (error) {
    console.error('Error during simulation:', error);
  }
};

const behavior = process.argv[2];

if (!behavior) {
  showUsage();
} else {
  simulateUserBehavior(behavior);
}
