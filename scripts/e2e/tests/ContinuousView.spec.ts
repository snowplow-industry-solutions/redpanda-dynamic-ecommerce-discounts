import { test, expect } from '@playwright/test'
import { viewProduct } from './testUtils'

test.describe('Continuous View Behavior', () => {
  test('should view a product for more than 90 seconds', async ({ page }) => {
    const product1 = 1
    const product2 = 2
    const product3 = 3
    const durationViewToGetADiscount = 100
    const totalSessions = 3

    console.log('Generating warm up events')
    await viewProduct(0, page, product1, 10, totalSessions)
    await viewProduct(1, page, product3, 20, totalSessions)

    console.log(`\n\nGenerating events expecting to get a discount for product ${product2}`)
    await viewProduct(2, page, product2, durationViewToGetADiscount, totalSessions)

    await expect(page).toHaveURL(`/product/${product2}`)
  })
})
