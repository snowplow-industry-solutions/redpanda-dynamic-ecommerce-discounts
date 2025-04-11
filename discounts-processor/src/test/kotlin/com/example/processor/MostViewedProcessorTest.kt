package com.example.processor

import com.example.model.DiscountEvent
import com.example.model.PagePingEvent
import io.kotest.core.spec.style.BehaviorSpec
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.mockito.kotlin.*
import java.time.Instant

class MostViewedProcessorTest : BehaviorSpec({
    val userId = "user1"
    val product1Id = "page_1"
    val product2Id = "page_2"
    val key = userId

    given("a most viewed processor") {
        val processor = spy(MostViewedProcessor())
        val lastDiscountTimestamp: ValueState<Long> = mock()
        val runtimeContext: RuntimeContext = mock()
        val collector: Collector<DiscountEvent> = mock()
        val window: TimeWindow = mock()
        val context: ProcessWindowFunction<PagePingEvent, DiscountEvent, String, TimeWindow>.Context = mock {
            on { window() } doReturn window
        }

        beforeTest {
            doReturn(runtimeContext).whenever(processor).runtimeContext
            whenever(runtimeContext.getState(any<ValueStateDescriptor<Long>>())).thenReturn(lastDiscountTimestamp)
            processor.open(Configuration())
            clearInvocations(collector)
            clearInvocations(lastDiscountTimestamp)
        }

        `when`("processing events within a 5-minute window") {
            val baseTime = Instant.parse("2024-01-01T10:00:00Z").toEpochMilli()
            val windowEnd = baseTime + (5 * 60 * 1000)

            beforeTest {
                whenever(context.currentWatermark()).thenReturn(windowEnd)
                whenever(lastDiscountTimestamp.value()).thenReturn(null)
                whenever(window.maxTimestamp()).thenReturn(windowEnd)
                whenever(window.getEnd()).thenReturn(windowEnd)
                whenever(window.start).thenReturn(baseTime)
            }

            and("one product has more views than others") {
                val events = createMixedEvents(
                    baseTime,
                    listOf(
                        ProductActivity(product1Id, 7),
                        ProductActivity(product2Id, 4)
                    )
                )

                then("should generate discount for most viewed product") {
                    processor.process(key, context, events, collector)
                    
                    verify(collector, times(1)).collect(argThat { 
                        this.userId == userId && 
                        this.productId == product1Id && 
                        this.discount.rate == 0.1 &&
                        this.discount.byNumberOfViews?.views == 7
                    })
                }
            }

            and("two products have same views but different durations") {
                val events = createMixedEventsWithDifferentIntervals(
                    baseTime,
                    listOf(
                        ProductActivity(product1Id, 5, 15000),
                        ProductActivity(product2Id, 5, 10000)
                    )
                )

                then("should generate discount for product with longer viewing time") {
                    processor.process(key, context, events, collector)
                    
                    verify(collector, times(1)).collect(argThat { 
                        this.userId == userId && 
                        this.productId == product1Id && 
                        this.discount.rate == 0.1 &&
                        this.discount.byNumberOfViews?.views == 5
                    })
                }
            }

            and("during cooldown period") {
                val lastDiscountTime = baseTime - (2 * 60 * 1000)
                val events = createMixedEvents(
                    baseTime,
                    listOf(ProductActivity(product1Id, 5))
                )

                beforeTest {
                    whenever(lastDiscountTimestamp.value()).thenReturn(lastDiscountTime)
                }

                then("should not generate discount") {
                    processor.process(key, context, events, collector)
                    verify(collector, never()).collect(any())
                }
            }

            and("insufficient views for any product") {
                val events = createMixedEvents(
                    baseTime,
                    listOf(
                        ProductActivity(product1Id, 2),
                        ProductActivity(product2Id, 2)
                    )
                )

                then("should not generate discount") {
                    processor.process(key, context, events, collector)
                    verify(collector, never()).collect(any())
                }
            }
        }
    }
})

data class ProductActivity(
    val productId: String,
    val views: Int,
    val intervalMs: Long = 10000
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
    activities: List<ProductActivity>
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
