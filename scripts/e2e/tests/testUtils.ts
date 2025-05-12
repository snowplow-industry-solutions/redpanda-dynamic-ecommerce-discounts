import { Page } from '@playwright/test'

export const wait = async (page: Page, seconds: number) => {
  await page.waitForTimeout(seconds * 1000)
}

export const scrollUpAndDown = async (page: Page) => {
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await wait(page, 1)
  await page.evaluate(() => window.scrollTo(0, 0))
  await wait(page, 1)
}

const getTimestamp = () => {
  return new Date().toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

export const viewProduct = async (
  index: number,
  page: Page,
  productId: number,
  duration: number,
  totalSessions: number
) => {
  await page.goto(`/product/${productId}`)
  console.log(
    `[${getTimestamp()}] Session ${index + 1}/${totalSessions}: Viewing product ${productId} for ${duration} seconds`
  )

  if (duration > 5) {
    const intervals = Math.floor(duration / 5)
    console.log(
      `[${getTimestamp()}] Scrolling up and down the product's page for ${intervals} times:`
    )
    for (let i = 0; i < intervals; i++) {
      await scrollUpAndDown(page)
      console.log(`[${getTimestamp()}] ${i + 1}/${intervals}`)
      await wait(page, 3)
    }
  } else {
    await wait(page, duration)
  }
}
