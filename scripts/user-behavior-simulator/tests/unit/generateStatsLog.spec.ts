import { test, expect } from '@playwright/test'
import { Product } from '../../src/types'
import { ProductStats } from '../../src/product-analytics'
import { generateStatsLog } from '../../src/kafka-simulators'

test.describe('generateStatsLog', () => {
  const mockProducts: Product[] = [
    { id: '1', name: 'Product A', price: 10.0, webpage_id: 'page_1' },
    { id: '2', name: 'Product B', price: 20.0, webpage_id: 'page_2' },
  ]

  test('should generate correct stats log with single product', () => {
    const stats = new Map<string, ProductStats>([['1', { views: 5, totalDuration: 100 }]])
    const progress = { percentComplete: 50.5 }
    const result = generateStatsLog(stats, mockProducts, progress)
    expect(result).toBe(
      '\nCycle progress: 50% - Current stats:\n' + '  "Product A": 5 views, 100s total'
    )
  })

  test('should generate correct stats log with multiple products', () => {
    const stats = new Map<string, ProductStats>([
      ['1', { views: 5, totalDuration: 100 }],
      ['2', { views: 3, totalDuration: 50 }],
    ])
    const progress = { percentComplete: 75.8 }
    const result = generateStatsLog(stats, mockProducts, progress)
    expect(result).toBe(
      '\nCycle progress: 75% - Current stats:\n' +
        '  "Product A": 5 views, 100s total\n' +
        '  "Product B": 3 views, 50s total'
    )
  })

  test('should handle unknown product IDs', () => {
    const stats = new Map<string, ProductStats>([['999', { views: 5, totalDuration: 100 }]])
    const progress = { percentComplete: 25.2 }
    const result = generateStatsLog(stats, mockProducts, progress)
    expect(result).toBe(
      '\nCycle progress: 25% - Current stats:\n' + '  "undefined": 5 views, 100s total'
    )
  })

  test('should round progress percentage correctly', () => {
    const stats = new Map<string, ProductStats>([['1', { views: 1, totalDuration: 10 }]])
    const progress = { percentComplete: 99.9 }
    const result = generateStatsLog(stats, mockProducts, progress)
    expect(result).toBe(
      '\nCycle progress: 99% - Current stats:\n' + '  "Product A": 1 views, 10s total'
    )
  })
})
