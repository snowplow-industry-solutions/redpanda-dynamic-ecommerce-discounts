package com.example.processor

import com.example.model.DiscountEvent
import com.example.model.PagePingEvent
import io.kotest.core.spec.style.BehaviorSpec
import org.apache.kafka.streams.processor.api.ProcessorContext
import org.apache.kafka.streams.processor.api.Record
import org.apache.kafka.streams.state.KeyValueStore
import org.mockito.kotlin.*
import java.time.Instant

class MostViewedProcessorTest : BehaviorSpec({
  val userId = "user1"
  val product1Id = "page_1"
  val product2Id = "page_2"

  given("a most viewed processor") {
    val processor = MostViewedProcessor()
    val context: ProcessorContext<String, DiscountEvent> = mock()
    val lastDiscountStore: KeyValueStore<String, Long> = mock()
    val viewsStore: KeyValueStore<String, Int> = mock()
    val durationStore: KeyValueStore<String, Long> = mock()

    beforeTest {
      whenever(context.getStateStore<KeyValueStore<String, Long>>("last-discount-store"))
        .thenReturn(lastDiscountStore)
      whenever(context.getStateStore<KeyValueStore<String, Int>>("views-store"))
        .thenReturn(viewsStore)
      whenever(context.getStateStore<KeyValueStore<String, Long>>("duration-store"))
        .thenReturn(durationStore)

      processor.init(context)
    }

    `when`("processing events within a 5-minute window") {
      val baseTime = Instant.parse("2024-01-01T10:00:00Z").toEpochMilli()
      val windowEnd = baseTime + (5 * 60 * 1000)

      beforeTest {
        whenever(lastDiscountStore.get(any<String>())).thenReturn(null)
      }

      and("one product has more views than others") {
        val events = createMixedEvents(
          baseTime,
          listOf(
            ProductActivity(product1Id, 7),
            ProductActivity(product2Id, 4),
          ),
        ).map { Record(userId, it, baseTime) }

        then("should generate discount for most viewed product") {
          events.forEach { processor.process(it) }

          verify(context).forward(
            argThat { record: Record<String, DiscountEvent> ->
              val discountEvent = record.value()
              discountEvent.userId == userId &&
                discountEvent.productId == product1Id &&
                discountEvent.discount.rate == 0.1 &&
                discountEvent.discount.byNumberOfViews?.views == 7
            },
          )
        }
      }

      and("two products have same views but different durations") {
        val events = createMixedEventsWithDifferentIntervals(
          baseTime,
          listOf(
            ProductActivity(product1Id, 5, 15000),
            ProductActivity(product2Id, 5, 10000),
          ),
        ).map { Record(userId, it, baseTime) }

        then("should generate discount for product with longer viewing time") {
          events.forEach { processor.process(it) }

          verify(context).forward(
            argThat { record: Record<String, DiscountEvent> ->
              val discountEvent = record.value()
              discountEvent.userId == userId &&
                discountEvent.productId == product1Id &&
                discountEvent.discount.rate == 0.1 &&
                discountEvent.discount.byNumberOfViews?.views == 5
            },
          )
        }
      }

      and("insufficient views for any product") {
        val events = createMixedEvents(
          baseTime,
          listOf(
            ProductActivity(product1Id, 2),
            ProductActivity(product2Id, 2),
          ),
        ).map { Record(userId, it, baseTime) }

        then("should not generate discount") {
          events.forEach { processor.process(it) }
          verify(context, never()).forward(any<Record<String, DiscountEvent>>())
        }
      }

      and("within the same 5-minute window of last discount") {
        val windowStart = baseTime
        val lastDiscountTime = windowStart + (1 * 60 * 1000)
        val newEventTime = lastDiscountTime + (2 * 60 * 1000)

        val events = createMixedEvents(
          newEventTime,
          listOf(ProductActivity(product1Id, 5)),
        ).map { Record(userId, it, newEventTime) }

        beforeTest {
          whenever(lastDiscountStore.get(any<String>())).thenReturn(lastDiscountTime)
        }

        then("should not generate new discount until window ends") {
          events.forEach { processor.process(it) }
          verify(context, never()).forward(any<Record<String, DiscountEvent>>())
        }
      }

      and("in a new 5-minute window after last discount") {
        val previousWindowStart = baseTime - (5 * 60 * 1000)
        val lastDiscountTime = previousWindowStart + (1 * 60 * 1000)
        val newEventTime = baseTime + (1 * 60 * 1000)

        val events = createMixedEvents(
          newEventTime,
          listOf(ProductActivity(product1Id, 5)),
        ).map { Record(userId, it, newEventTime) }

        beforeTest {
          whenever(lastDiscountStore.get(any<String>())).thenReturn(lastDiscountTime)
        }

        then("should allow new discount generation") {
          events.forEach { processor.process(it) }
          verify(context).forward(
            argThat { record: Record<String, DiscountEvent> ->
              val discountEvent = record.value()
              discountEvent.userId == userId &&
                discountEvent.productId == product1Id &&
                discountEvent.discount.rate == 0.1 &&
                discountEvent.discount.byNumberOfViews?.views == 5
            },
          )
        }
      }
    }
  }
})

data class ProductActivity(
  val productId: String,
  val views: Int,
  val intervalMs: Long = 10000,
)

fun createMixedEvents(baseTime: Long, activities: List<ProductActivity>): List<PagePingEvent> {
  return activities.flatMap { activity ->
    (0 until activity.views).map { i ->
      PagePingEvent().apply {
        collectorTimestamp = Instant.ofEpochMilli(baseTime + (i * activity.intervalMs))
        userId = "user1"
        webpageId = activity.productId
        eventName = "page_ping"
      }
    }
  }.sortedBy { it.collectorTimestamp }
}

fun createMixedEventsWithDifferentIntervals(
  baseTime: Long,
  activities: List<ProductActivity>,
): List<PagePingEvent> {
  return activities.flatMap { activity ->
    (0 until activity.views).map { i ->
      PagePingEvent().apply {
        collectorTimestamp = Instant.ofEpochMilli(baseTime + (i * activity.intervalMs))
        userId = "user1"
        webpageId = activity.productId
        eventName = "page_ping"
      }
    }
  }.sortedBy { it.collectorTimestamp }
}
