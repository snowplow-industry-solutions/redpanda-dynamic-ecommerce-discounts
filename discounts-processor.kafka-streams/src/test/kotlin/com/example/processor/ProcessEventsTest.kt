package com.example.processor

import com.example.config.ConfigurationManager
import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.Properties

private const val WINDOW_DURATION_SECONDS = 300
private const val PING_INTERVAL_SECONDS = 10
private const val MIN_PINGS_FOR_DISCOUNT = 3
private const val DISCOUNT_RATE = 0.1

private const val SUFFICIENT_PINGS = MIN_PINGS_FOR_DISCOUNT + 1
private const val INSUFFICIENT_PINGS = MIN_PINGS_FOR_DISCOUNT - 1

class ProcessEventsTest : BehaviorSpec({
  val userId = "user123"
  val productId = "prod456"
  val webpageId = "page789"
  val productName = "Test Product"
  val productPrice = 99.99
  val baseTime = Instant.parse("2024-01-01T10:00:00Z")

  given("a continuous view processor") {
    lateinit var processor: ContinuousViewProcessor

    beforeTest {
      val testProperties = Properties().apply {
        setProperty("processor.continuous-view.enabled", "true")
        setProperty("processor.window.duration.seconds", WINDOW_DURATION_SECONDS.toString())
        setProperty(
          "processor.continuous-view.ping-interval.seconds",
          PING_INTERVAL_SECONDS.toString(),
        )
        setProperty(
          "processor.continuous-view.min-pings-for-discount",
          MIN_PINGS_FOR_DISCOUNT.toString(),
        )
        setProperty("processor.discount-rate", DISCOUNT_RATE.toString())
      }
      ConfigurationManager.getInstance(testProperties)
      processor = ContinuousViewProcessor()
    }

    `when`("processing events with sufficient continuous views") {
      val events = mutableListOf<PagePingEvent>()

      beforeTest {
        val productView = ProductViewEvent()
        productView.collectorTimestamp = baseTime
        productView.eventName = "product_view"
        productView.userId = userId
        productView.productId = productId
        productView.productName = productName
        productView.productPrice = productPrice
        productView.webpageId = webpageId
        events.add(productView)

        (1..SUFFICIENT_PINGS).forEach { i ->
          val pingEvent = PagePingEvent()
          pingEvent.collectorTimestamp = baseTime.plusSeconds(i * PING_INTERVAL_SECONDS.toLong())
          pingEvent.eventName = "page_ping"
          pingEvent.userId = userId
          pingEvent.webpageId = webpageId
          events.add(pingEvent)
        }
      }

      then("should generate discount") {
        val result = processor.processEvents(userId, events)
        result.isPresent shouldBe true

        val discount = result.get()
        discount.userId shouldBe userId
        discount.productId shouldBe productId
        discount.discount.rate shouldBe DISCOUNT_RATE
        discount.discount.byViewTime?.durationInSeconds shouldBe (SUFFICIENT_PINGS * PING_INTERVAL_SECONDS).toLong()
      }
    }

    `when`("processing events with insufficient pings") {
      val events = mutableListOf<PagePingEvent>()

      beforeTest {
        val productView = ProductViewEvent()
        productView.collectorTimestamp = baseTime
        productView.eventName = "product_view"
        productView.userId = userId
        productView.productId = productId
        productView.productName = productName
        productView.productPrice = productPrice
        productView.webpageId = webpageId
        events.add(productView)

        (1..INSUFFICIENT_PINGS).forEach { i ->
          val pingEvent = PagePingEvent()
          pingEvent.collectorTimestamp = baseTime.plusSeconds(i * PING_INTERVAL_SECONDS.toLong())
          pingEvent.eventName = "page_ping"
          pingEvent.userId = userId
          pingEvent.webpageId = webpageId
          events.add(pingEvent)
        }
      }

      then("should not generate discount") {
        val result = processor.processEvents(userId, events)
        result.isPresent shouldBe false
      }
    }
  }
})
