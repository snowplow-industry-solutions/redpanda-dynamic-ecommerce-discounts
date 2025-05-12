import { test, expect } from '@playwright/test'
import { viewProduct } from './testUtils'

test.describe('Product Viewing Pattern', () => {
  test('should follow specific viewing pattern', async ({ page }) => {
    test.setTimeout(360000)

    const config = {
      productIdRange: {
        min: 1,
        max: 6,
      },
      minViewsForDiscountEligibility: 5,
      productsWithManyViews: 2,
      maxViewDuration: 60,
      totalViewSessions: 20,
      maxTotalDuration: 300,
      minProducts: 4,
    }

    interface ViewSession {
      productId: number
      duration: number
    }

    const getRandomNumber = (min: number, max: number): number => {
      return Math.floor(Math.random() * (max - min + 1)) + min
    }

    const getRandomDuration = (maxDuration: number, remainingTime: number): number => {
      const effectiveMaxDuration = Math.min(maxDuration, remainingTime)
      return getRandomNumber(1, effectiveMaxDuration)
    }

    const generateViewSessions = (): ViewSession[] => {
      const sessions: ViewSession[] = []
      let remainingTime = config.maxTotalDuration
      const productViews: { [key: number]: number } = {}

      const productsWithManyViews = new Set<number>()
      while (productsWithManyViews.size < config.productsWithManyViews) {
        const productId = getRandomNumber(config.productIdRange.min, config.productIdRange.max)
        productsWithManyViews.add(productId)
      }

      productsWithManyViews.forEach(productId => {
        const views = getRandomNumber(
          config.minViewsForDiscountEligibility,
          config.minViewsForDiscountEligibility + 3
        )
        const timePerView = Math.floor(remainingTime / (config.totalViewSessions - sessions.length))

        for (let i = 0; i < views; i++) {
          const duration = getRandomDuration(
            Math.min(config.maxViewDuration, timePerView),
            remainingTime
          )
          sessions.push({
            productId,
            duration,
          })
          remainingTime -= duration
        }
        productViews[productId] = views
      })

      const remainingSessions = config.totalViewSessions - sessions.length
      const remainingProducts = Array.from(
        { length: config.productIdRange.max - config.productIdRange.min + 1 },
        (_, i) => i + config.productIdRange.min
      )

      const usedProducts = new Set(Object.keys(productViews).map(Number))
      const requiredAdditionalProducts = Math.max(0, config.minProducts - usedProducts.size)

      if (requiredAdditionalProducts > 0) {
        const availableProducts = remainingProducts.filter(id => !usedProducts.has(id))
        const productsToAdd = availableProducts.slice(0, requiredAdditionalProducts)

        productsToAdd.forEach(productId => {
          const duration = getRandomDuration(
            Math.min(
              config.maxViewDuration,
              Math.floor(remainingTime / requiredAdditionalProducts)
            ),
            remainingTime
          )
          sessions.push({
            productId,
            duration,
          })
          remainingTime -= duration
          productViews[productId] = 1
        })
      }

      const remainingSessionsAfterMinProducts = config.totalViewSessions - sessions.length

      for (let i = 0; i < remainingSessionsAfterMinProducts; i++) {
        const availableProducts = remainingProducts.filter(
          productId => productId !== sessions[sessions.length - 1]?.productId
        )

        const productId = availableProducts[getRandomNumber(0, availableProducts.length - 1)]
        const timePerView = Math.floor(remainingTime / (remainingSessionsAfterMinProducts - i))
        const duration = getRandomDuration(
          Math.min(config.maxViewDuration, timePerView),
          remainingTime
        )

        sessions.push({
          productId,
          duration,
        })
        remainingTime -= duration
        productViews[productId] = (productViews[productId] || 0) + 1
      }

      const shuffledSessions: ViewSession[] = []
      const availableSessions = [...sessions]

      while (availableSessions.length > 0) {
        const lastProductId = shuffledSessions[shuffledSessions.length - 1]?.productId
        const availableForNext = availableSessions.filter(
          session => session.productId !== lastProductId
        )

        if (availableForNext.length === 0) {
          return generateViewSessions()
        }

        const nextIndex = getRandomNumber(0, availableForNext.length - 1)
        const nextSession = availableForNext[nextIndex]

        const originalIndex = availableSessions.indexOf(nextSession)
        availableSessions.splice(originalIndex, 1)

        shuffledSessions.push(nextSession)
      }

      return shuffledSessions
    }

    const viewSessions = generateViewSessions()

    const productStats = viewSessions.reduce(
      (acc, session) => {
        if (!acc[session.productId]) {
          acc[session.productId] = { views: 0, totalDuration: 0 }
        }
        acc[session.productId].views++
        acc[session.productId].totalDuration += session.duration
        return acc
      },
      {} as { [key: number]: { views: number; totalDuration: number } }
    )

    console.log('Test Configuration:', config)
    console.log('\nProducts eligible ✅ for a discount:')
    Object.entries(productStats).forEach(([productId, stats]) => {
      const status = stats.views >= config.minViewsForDiscountEligibility ? '✅' : '❌'
      const minutes = Math.floor(stats.totalDuration / 60)
      const seconds = stats.totalDuration % 60
      console.log(
        `Product ${productId}: ${stats.views} views (${minutes.toString().padStart(2, '0')}:${seconds
          .toString()
          .padStart(2, '0')}) ${status}`
      )
    })

    console.log('\nGenerated View Sessions:')
    viewSessions.forEach((session, index) => {
      console.log(
        `Session ${index + 1}: Product ${session.productId} for ${session.duration} seconds`
      )
    })

    const totalSeconds = viewSessions.reduce((total, session) => total + session.duration, 0)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    console.log(
      `\nTotal duration: ${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}\n`
    )

    const totalSessions = viewSessions.length
    console.log(`Starting visualization of ${totalSessions} sessions...`)
    for (const [index, session] of viewSessions.entries()) {
      await viewProduct(index, page, session.productId, session.duration, totalSessions)
    }

    const lastSession = viewSessions[viewSessions.length - 1]
    await expect(page).toHaveURL(`/product/${lastSession.productId}`)
  })
})
