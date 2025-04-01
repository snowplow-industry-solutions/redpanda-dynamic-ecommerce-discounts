import { test, expect } from '@playwright/test';

test.describe('Product Viewing Pattern', () => {
  test('should follow specific viewing pattern', async ({ page }) => {
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

    await viewProduct(2, 30);

    await viewProduct(3, 10);

    await viewProduct(4, 5);

    await viewProduct(2, 30);

    await viewProduct(1, 10);

    await page.goto('/category/men-shoes');
    console.log('Back to category list for 10 seconds');
    await wait(10);

    await viewProduct(2, 40);

    await expect(page).toHaveURL('/product/2');
  });
});
