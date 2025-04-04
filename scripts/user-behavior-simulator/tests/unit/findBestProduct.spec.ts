import { test, expect } from '@playwright/test'
import { Product } from '../../src/types'
import { findBestProduct, ProductStats } from '../../src/product-analytics'

test.describe('findBestProduct', () => {
  const mockProducts: Product[] = [
    { id: '1', name: 'Product A', price: 10.0, webpage_id: 'page_1' },
    { id: '2', name: 'Product B', price: 20.0, webpage_id: 'page_2' },
    { id: '3', name: 'Product C', price: 30.0, webpage_id: 'page_3' },
  ]

  test('should return null when stats map is empty', () => {
    const emptyStats = new Map<string, ProductStats>()
    const result = findBestProduct(mockProducts, emptyStats)
    expect(result).toBeNull()
  })

  test('should return product with most views', () => {
    const stats = new Map<string, ProductStats>([
      ['1', { views: 5, totalDuration: 100 }],
      ['2', { views: 10, totalDuration: 50 }],
      ['3', { views: 3, totalDuration: 200 }],
    ])

    const result = findBestProduct(mockProducts, stats)
    expect(result).toEqual(mockProducts[1]) // Product B has most views
  })

  test('should use totalDuration as tiebreaker when views are equal', () => {
    const stats = new Map<string, ProductStats>([
      ['1', { views: 5, totalDuration: 100 }],
      ['2', { views: 5, totalDuration: 200 }],
      ['3', { views: 5, totalDuration: 150 }],
    ])

    const result = findBestProduct(mockProducts, stats)
    expect(result).toEqual(mockProducts[1]) // Product B has highest duration
  })

  test('should handle products with no stats', () => {
    const stats = new Map<string, ProductStats>([
      ['1', { views: 5, totalDuration: 100 }],
      // Product 2 has no stats
      ['3', { views: 3, totalDuration: 200 }],
    ])

    const result = findBestProduct(mockProducts, stats)
    expect(result).toEqual(mockProducts[0]) // Product A is best among those with stats
  })

  test('should handle empty product list', () => {
    const stats = new Map<string, ProductStats>([['1', { views: 5, totalDuration: 100 }]])

    const result = findBestProduct([], stats)
    expect(result).toBeNull()
  })

  test('should handle products with same views and duration', () => {
    const stats = new Map<string, ProductStats>([
      ['1', { views: 5, totalDuration: 100 }],
      ['2', { views: 5, totalDuration: 100 }],
      ['3', { views: 5, totalDuration: 100 }],
    ])

    const result = findBestProduct(mockProducts, stats)
    expect(mockProducts.some(p => p === result)).toBeTruthy()
  })
})
