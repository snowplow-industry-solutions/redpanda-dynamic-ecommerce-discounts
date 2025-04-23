import { createBaseTime, runTestScenario } from '../common.js';

const ALL_PRODUCTS = [
  { id: 'product1', name: 'SP Dunk Low Retro', price: 119.99 },
  { id: 'product2', name: 'SP Air Force 1 Shadow', price: 129.99 },
  { id: 'product3', name: 'Total Orange', price: 129.99 },
  { id: 'product4', name: 'SP Air Max Plus 3', price: 189.99 },
  { id: 'product5', name: 'SP Flex Runner 2', price: 42.99 },
  { id: 'product6', name: 'SP React Vision', price: 159.99 },
  { id: 'product7', name: 'SP Zoom Pegasus', price: 139.99 },
  { id: 'product8', name: 'SP Metcon 8', price: 149.99 },
  { id: 'product9', name: 'SP Free Run 5.0', price: 109.99 },
  { id: 'product10', name: 'SP Air Zoom Structure', price: 169.99 }
];

const USER_ID = 'user1';

function shuffleArray(array) {
  for (let i = array.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [array[i], array[j]] = [array[j], array[i]];
  }
  return array;
}

function getRandomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export async function createEvents(timeOffset, options = {}) {
  const {
    maxProducts = 10,
    maxPingsPerProduct = 7
  } = options;

  const baseTime = createBaseTime(timeOffset);
  const events = [];

  const numProducts = getRandomInt(1, Math.min(maxProducts, ALL_PRODUCTS.length));
  const selectedProducts = shuffleArray([...ALL_PRODUCTS]).slice(0, numProducts);

  const createProductEvents = (product, startOffset) => {
    const productTime = new Date(baseTime);
    productTime.setSeconds(productTime.getSeconds() + startOffset);

    const pingCount = getRandomInt(1, maxPingsPerProduct);

    events.push({
      collector_tstamp: new Date(productTime).toISOString(),
      event_name: 'product_view',
      user_id: USER_ID,
      webpage_id: `page_${product.id}`,
      product_id: product.id,
      product_name: product.name,
      product_price: product.price
    });

    for (let i = 1; i <= pingCount; i++) {
      const timestamp = new Date(productTime);
      timestamp.setSeconds(timestamp.getSeconds() + (i * 10));

      events.push({
        collector_tstamp: timestamp.toISOString(),
        event_name: 'page_ping',
        user_id: USER_ID,
        webpage_id: `page_${product.id}`
      });
    }

    return pingCount;
  };

  let currentOffset = 0;
  const productSummary = selectedProducts.map(product => {
    const pingCount = createProductEvents(product, currentOffset);
    currentOffset += (pingCount + 1) * 10;
    return {
      id: product.id,
      name: product.name,
      pings: pingCount
    };
  });

  console.log('\nTest Summary:');
  console.log(`Total products selected: ${selectedProducts.length}`);
  console.log('Products and their ping counts:');
  productSummary.forEach(p => {
    console.log(`- ${p.name} (${p.id}): ${p.pings} pings`);
  });
  console.log('');

  return events.sort((a, b) =>
    new Date(a.collector_tstamp) - new Date(b.collector_tstamp)
  );
}

export async function runTest(timeOffset, force = false, options = {}) {
  try {
    const { outputJson, discountEvent } = await runTestScenario({
      testName: 'MostViewed',
      testDescription: 'Check most-viewed products test',
      createEvents: () => createEvents(timeOffset, options),
      timeOffset,
      force,
      userId: USER_ID
    });

    if (outputJson.key === USER_ID &&
        discountEvent.user_id === USER_ID &&
        discountEvent.discount &&
        discountEvent.discount.rate === 0.1 &&
        typeof discountEvent.generated_at === 'string' &&
        /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(discountEvent.generated_at)) {
      console.log('✅ Test passed! Discount was generated correctly');
      console.log('Output:', JSON.stringify(outputJson, null, 2));
    } else {
      console.log('❌ Test failed! Discount was not generated as expected');
      console.log('Expected:', {
        key: USER_ID,
        user_id: USER_ID,
        discount: { rate: 0.1 },
        generated_at: 'ISO-8601 timestamp (e.g. 2023-10-21T12:34:56.789Z)'
      });
      console.log('Received:', JSON.stringify(outputJson, null, 2));
    }
  } catch (error) {
    console.error('Error running most-viewed test:', error);
    throw error;
  }
}
