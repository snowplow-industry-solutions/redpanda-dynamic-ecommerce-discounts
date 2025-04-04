import { Product } from './types';

export interface ProductStats {
  views: number;
  totalDuration: number;
}

export type ProductWithStats = {
  product: Product;
  stats: ProductStats;
};

const toProductWithStats = (stats: Map<string, ProductStats>) => (
  product: Product
): ProductWithStats | null => {
  const productStats = stats.get(product.id);
  return productStats ? { product, stats: productStats } : null;
};

const compareProducts = (a: ProductWithStats, b: ProductWithStats): number => {
  if (a.stats.views !== b.stats.views) {
    return b.stats.views - a.stats.views;
  }
  return b.stats.totalDuration - a.stats.totalDuration;
};

export const findBestProduct = (
  products: Product[],
  stats: Map<string, ProductStats>
): Product | null => {
  return products
    .map(toProductWithStats(stats))
    .filter((item): item is ProductWithStats => item !== null)
    .sort(compareProducts)
    .map(item => item.product)
    .at(0) ?? null;
};