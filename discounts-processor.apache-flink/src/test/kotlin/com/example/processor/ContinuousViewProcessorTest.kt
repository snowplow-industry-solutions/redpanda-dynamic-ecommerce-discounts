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

class ContinuousViewProcessorTest : BehaviorSpec({
    val userId = "user1"
    val webpageId = "page1"
    val key = "${userId}_${webpageId}"

    given("a continuous view processor") {
        val processor = spy(ContinuousViewProcessor())
        val lastDiscountTimestamp: ValueState<Long> = mock()
        val runtimeContext: RuntimeContext = mock()
        val collector: Collector<DiscountEvent> = mock()
        val context: ProcessWindowFunction<PagePingEvent, DiscountEvent, String, TimeWindow>.Context = mock()
        val window: TimeWindow = mock()

        beforeTest {
            doReturn(runtimeContext).whenever(processor).runtimeContext
            whenever(runtimeContext.getState(any<ValueStateDescriptor<Long>>())).thenReturn(lastDiscountTimestamp)
            processor.open(Configuration())
            clearInvocations(collector)
            clearInvocations(lastDiscountTimestamp)
        }

        `when`("receiving 9 page view events within 90 seconds") {
            val baseTime = Instant.now().toEpochMilli()
            val events = createPageViewEvents(9, baseTime, 11250)

            beforeTest {
                val windowEnd = baseTime + 90000
                whenever(context.currentWatermark()).thenReturn(windowEnd)
                whenever(lastDiscountTimestamp.value()).thenReturn(null)
                whenever(window.maxTimestamp()).thenReturn(windowEnd)
                whenever(window.getEnd()).thenReturn(windowEnd)
                whenever(window.start).thenReturn(baseTime)
            }

            then("should generate a discount") {
                processor.process(key, context, events, collector)
                
                verify(collector, times(1)).collect(argThat { 
                    this.userId == userId && 
                    this.productId == webpageId && 
                    this.discount.rate == 0.1
                })
                verify(lastDiscountTimestamp, times(1)).update(any())
            }
        }

        `when`("receiving events during cooldown period") {
            val baseTime = Instant.now().toEpochMilli()
            val lastDiscountTime = baseTime - (2 * 60 * 1000)
            val events = createPageViewEvents(9, baseTime, 8000)

            beforeTest {
                whenever(context.currentWatermark()).thenReturn(baseTime)
                whenever(lastDiscountTimestamp.value()).thenReturn(lastDiscountTime)
                whenever(window.maxTimestamp()).thenReturn(baseTime + 90000)
                whenever(window.getEnd()).thenReturn(baseTime + 90000)
            }

            then("should not generate a discount") {
                processor.process(key, context, events, collector)
                verify(collector, never()).collect(any())
                verify(lastDiscountTimestamp, never()).update(any())
            }
        }

        `when`("receiving insufficient events") {
            val baseTime = Instant.now().toEpochMilli()
            val events = createPageViewEvents(8, baseTime, 8000)

            beforeTest {
                whenever(context.currentWatermark()).thenReturn(baseTime)
                whenever(lastDiscountTimestamp.value()).thenReturn(null)
                whenever(window.maxTimestamp()).thenReturn(baseTime + 90000)
                whenever(window.getEnd()).thenReturn(baseTime + 90000)
            }

            then("should not generate a discount") {
                processor.process(key, context, events, collector)
                verify(collector, never()).collect(any())
                verify(lastDiscountTimestamp, never()).update(any())
            }
        }

        `when`("receiving events after cooldown expires") {
            val baseTime = Instant.now().toEpochMilli()
            val lastDiscountTime = baseTime - (6 * 60 * 1000)
            val events = createPageViewEvents(9, baseTime, 11250)

            beforeTest {
                val windowEnd = baseTime + 90000
                whenever(context.currentWatermark()).thenReturn(windowEnd)
                whenever(lastDiscountTimestamp.value()).thenReturn(lastDiscountTime)
                whenever(window.maxTimestamp()).thenReturn(windowEnd)
                whenever(window.getEnd()).thenReturn(windowEnd)
                whenever(window.start).thenReturn(baseTime)
            }

            then("should generate a discount") {
                processor.process(key, context, events, collector)
                
                verify(collector, times(1)).collect(argThat { 
                    this.userId == userId && 
                    this.productId == webpageId && 
                    this.discount.rate == 0.1
                })
                verify(lastDiscountTimestamp, times(1)).update(any())
            }
        }
    }
}) {
    companion object {
        private fun createPageViewEvents(
            count: Int,
            baseTime: Long,
            interval: Long
        ): List<PagePingEvent> = List(count) { i ->
            PagePingEvent().apply {
                userId = "user1"
                webpageId = "page1"
                eventName = "page_ping"
                collectorTimestamp = Instant.ofEpochMilli(baseTime + (i * interval))
            }
        }
    }
}
