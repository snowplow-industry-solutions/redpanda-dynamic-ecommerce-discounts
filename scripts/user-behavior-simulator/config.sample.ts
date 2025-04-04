import { Config } from './src/types.js'

const config: Config = {
  kafka: {
    clientId: 'user-behavior-simulator',
    brokers: ['localhost:19092'],
    topic: 'snowplow-enriched-good',
  },
  logging: {
    console: {
      level: 'info',
      timestamp: false,
      showLevel: false,
    },
    file: {
      level: 'debug',
      timestamp: true,
      showLevel: true,
    },
  },
  simulation: {
    cycle: {
      duration: 5 * 60,
      warningInterval: 30,
    },
    longView: {
      pagePingInterval: 10,
      duration: 90,
    },
    frequentView: {
      minDuration: 3,
      maxDuration: 13,
    },
    betweenLongViewInterval: 10,
    betweenNormalViewInterval: 30,
    snowplowEcommerceActionInterval: 2 + 10,
  },
  mocks: {
    users: [
      { id: '1', name: 'sion@oso.com' },
      { id: '2', name: 'paulo@oso.com' },
      { id: '3', name: 'alex@snowplowanalytics.com' },
      { id: '4', name: 'trent@snowplowanalytics.com' },
      { id: '5', name: 'lucas@snowplowanalytics.com' },
    ],
    products: [
      {
        id: '1',
        name: 'SP Dunk Low Retro',
        price: 119.99,
        webpage_id: 'page_1',
      },
      {
        id: '2',
        name: 'SP Air Max Plus 3',
        price: 189.99,
        webpage_id: 'page_2',
      },
      {
        id: '3',
        name: 'Total Orange',
        price: 129.99,
        webpage_id: 'page_3',
      },
      {
        id: '4',
        name: 'SP Air Force 1 Shadow',
        price: 129.99,
        webpage_id: 'page_4',
      },
      {
        id: '5',
        name: 'SP Flex Runner 2',
        price: 42.99,
        webpage_id: 'page_5',
      },
    ],
  },
}

export default config
