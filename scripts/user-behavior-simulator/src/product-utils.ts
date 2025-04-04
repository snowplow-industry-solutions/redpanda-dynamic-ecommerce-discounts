import { Product } from './types';
import { ProductStats } from './product-analytics';

export const getRandomProduct = (products: Product[]): Product =>
  products[Math.floor(Math.random() * products.length)];

export const updateProductStats = (
  product: Product,
  isPing: boolean,
  productStats: Map<string, ProductStats>
): void => {
  if (!isPing) {
    const currentStats = productStats.get(product.id) || {
      views: 0,
      totalDuration: 0,
    };
    productStats.set(product.id, {
      views: currentStats.views + 1,
      totalDuration: currentStats.totalDuration,
    });
  }
};

export const sleepSeconds = (seconds: number, message?: string): Promise<void> => {
  if (message) {
    console.log(message);
  }
  return new Promise(resolve => setTimeout(resolve, seconds * 1000));
};