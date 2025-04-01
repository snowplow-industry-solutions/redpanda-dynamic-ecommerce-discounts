export default {
  kafka: {
    clientId: 'user-behavior-simulator',
    brokers: ['localhost:19092'],
    topic: 'snowplow-enriched-good'
  },
  snowplow: {
    endpoint: 'localhost',
    port: 9090
  },
  simulation: {
    // note: all intervals are in seconds
    cycleDuration: 30, // duration of the cycle
    warningInCycleInterval: 30, // warning interval within one cycle
    pagePingInterval: 10, // interval between page pings
    afterActionEventInterval: 2,  // interval after an action event
    betweenLongViewInterval: 10,  // interval between long view simulations
    betweenNormalViewInterval: 30,  // interval between frequency simulations
    longViewInterval: 90, // duration of a long view
    viewsToBeConsideredFrequent: 3, // number of views to be considered frequent
    fixedUserId: 'ea580496-9a9a-4def-9dba-df8fc1290148',
    // new configurations for frequent view simulation
    frequentView: {
      minViewDuration: 1,  // minimum view duration in seconds
      maxViewDuration: 5  // maximum view duration in seconds
    }
  },
  mocks: {
    users: [
      'sion@oso.com',
      'paulo@oso.com',
      'alex@snowplowanalytics.com',
      'trent@snowplowanalytics.com',
      'lucas@snowplowanalytics.com',
    ],
    products: [
      { id: '1', name: 'SP Dunk Low Retro', price: 119.99, currency: 'USD', category: 'Shoes', webpage_id: 'page_1' },
      { id: '2', name: 'SP Air Max Plus 3', price: 189.99, currency: 'USD', category: 'Shoes', webpage_id: 'page_2' },
      { id: '3', name: 'Total Orange', price: 129.99, currency: 'USD', category: 'Shoes', webpage_id: 'page_3' },
      { id: '4', name: 'SP Air Force 1 Shadow', price: 129.99, currency: 'USD', category: 'Shoes', webpage_id: 'page_4' },
      { id: '5', name: 'SP Flex Runner 2', price: 42.99, currency: 'USD', category: 'Shoes', webpage_id: 'page_5' }
    ]
  }
};
