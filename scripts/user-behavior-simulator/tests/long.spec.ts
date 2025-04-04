import { test, expect } from '@playwright/test';

test.describe('Long View Behavior', () => {
  test('should view a product for more than 90 seconds', async ({ page }) => {
    const wait = async (seconds: number) => {
      await page.waitForTimeout(seconds * 1000);
      console.log(`Waited for ${seconds} seconds`);
    };

    const viewProduct = async (productId: number, duration: number) => {
      await page.goto(`/product/${productId}`);
      console.log(`Viewing product ${productId} for ${duration} seconds`);
      await wait(duration);
    };

    await page.goto('/category/men-shoes');
    console.log('Started at men-shoes category');
    wait(5);

    await viewProduct(2, 93);

    await expect(page).toHaveURL('/product/2');
  });
});
