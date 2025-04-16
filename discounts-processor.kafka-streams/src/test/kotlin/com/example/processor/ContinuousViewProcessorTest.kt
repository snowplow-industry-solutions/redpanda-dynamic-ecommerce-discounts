package com.example.processor

import com.example.config.ConfigurationManager
import com.example.model.DiscountEvent
import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import io.kotest.core.spec.style.BehaviorSpec
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.WindowStore
import org.mockito.kotlin.*
import java.time.Duration
import java.time.Instant
import java.util.ArrayList
import java.util.Properties

class ContinuousViewProcessorTest : BehaviorSpec() {
  private val userId = "user1"
  private val productId = "product1"
  private val webpageId = "webpage1"
  private val productName = "Test Product"
  private val productPrice = 99.99

  init {
    given("a continuous view processor") {
      val processor = ContinuousViewProcessor()

      beforeTest {
        val testProperties = Properties().apply {
          setProperty("processor.continuous-view.enabled", "true")
          setProperty("processor.window.duration.seconds", "300")
          setProperty("processor.continuous-view.ping-interval.seconds", "20")
          setProperty("processor.continuous-view.min-pings-for-discount", "3")
          setProperty("processor.discount-rate", "0.1")
        }
        ConfigurationManager.getInstance(testProperties)
      }

      `when`("processing continuous view events") {
        val baseTime = Instant.parse("2024-01-01T10:00:00Z")
        val windowSize = Duration.ofMinutes(5)
        val advanceSize = Duration.ofMinutes(1)

        and("user views product continuously meeting minimum duration") {
          val events = ArrayList<PagePingEvent>()

          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime
              eventName = "product_view"
              this.userId = this@ContinuousViewProcessorTest.userId
              this.productId = this@ContinuousViewProcessorTest.productId
              this.productName = this@ContinuousViewProcessorTest.productName
              this.productPrice = productPrice
              this.webpageId = this@ContinuousViewProcessorTest.webpageId
            },
          )

          (1..4).forEach { i ->
            events.add(
              PagePingEvent().apply {
                collectorTimestamp = baseTime.plusSeconds(i * 15L)
                eventName = "page_ping"
                this.userId = this@ContinuousViewProcessorTest.userId
                this.webpageId = this@ContinuousViewProcessorTest.webpageId
              },
            )
          }

          then("should generate discount for continuous viewing") {
            val inputStream: KStream<String, PagePingEvent> = mock()
            val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
            val windowedStream: SessionWindowedKStream<String, PagePingEvent> = mock()
            val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

            whenever(inputStream.groupByKey()).thenReturn(groupedStream)
            whenever(
              groupedStream.windowedBy(any<SessionWindows>()),
            ).thenReturn(windowedStream)
            whenever(
              windowedStream.aggregate(
                any<Initializer<ArrayList<PagePingEvent>>>(),
                any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                any<Merger<String, ArrayList<PagePingEvent>>>(),
                any(),
              ),
            ).thenReturn(aggregatedStream)
            whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
            whenever(suppressedStream.toStream()).thenReturn(finalStream)
            whenever(
              finalStream.peek(
                any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>(),
              ),
            )
              .thenReturn(peekedStream)
            whenever(
              peekedStream.flatMapValues(
                any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
              ),
            )
              .thenReturn(resultStream)

            val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>(
              "test-store",
            )

            processor.addProcessing(
              inputStream,
              windowSize,
              advanceSize,
              materialized,
            )

            verify(
              finalStream,
            ).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
            verify(
              peekedStream,
            ).flatMapValues(
              any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
            )
          }
        }

        and("user views product for less than minimum duration") {
          val events = ArrayList<PagePingEvent>()

          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime
              eventName = "product_view"
              this.userId = this@ContinuousViewProcessorTest.userId
              this.productId = this@ContinuousViewProcessorTest.productId
              this.productName = this@ContinuousViewProcessorTest.productName
              this.productPrice = productPrice
              this.webpageId = this@ContinuousViewProcessorTest.webpageId
            },
          )

          (1..2).forEach { i ->
            events.add(
              PagePingEvent().apply {
                collectorTimestamp = baseTime.plusSeconds(i * 15L)
                eventName = "page_ping"
                this.userId = this@ContinuousViewProcessorTest.userId
                this.webpageId = this@ContinuousViewProcessorTest.webpageId
              },
            )
          }

          then("should not generate discount") {
            val inputStream: KStream<String, PagePingEvent> = mock()
            val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
            val windowedStream: SessionWindowedKStream<String, PagePingEvent> = mock()
            val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

            whenever(inputStream.groupByKey()).thenReturn(groupedStream)
            whenever(
              groupedStream.windowedBy(any<SessionWindows>()),
            ).thenReturn(windowedStream)
            whenever(
              windowedStream.aggregate(
                any<Initializer<ArrayList<PagePingEvent>>>(),
                any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                any<Merger<String, ArrayList<PagePingEvent>>>(),
                any(),
              ),
            ).thenReturn(aggregatedStream)
            whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
            whenever(suppressedStream.toStream()).thenReturn(finalStream)
            whenever(
              finalStream.peek(
                any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>(),
              ),
            )
              .thenReturn(peekedStream)
            whenever(
              peekedStream.flatMapValues(
                any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
              ),
            )
              .thenReturn(resultStream)

            val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>(
              "test-store",
            )

            processor.addProcessing(
              inputStream,
              windowSize,
              advanceSize,
              materialized,
            )

            verify(
              finalStream,
            ).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
            verify(
              peekedStream,
            ).flatMapValues(
              any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
            )

            val flatMapCaptor =
              argumentCaptor<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()
            verify(peekedStream).flatMapValues(flatMapCaptor.capture())

            val testKey = mock<Windowed<String>>()
            whenever(testKey.key()).thenReturn(userId)
            val result = flatMapCaptor.firstValue.apply(testKey, events)
            assert(
              result.toList().isEmpty(),
            ) { "Expected no discounts to be generated" }
          }
        }

        and("insufficient ping events") {
          val events = ArrayList<PagePingEvent>()

          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime
              eventName = "product_view"
              this.userId = this@ContinuousViewProcessorTest.userId
              this.productId = this@ContinuousViewProcessorTest.productId
              this.productName = this@ContinuousViewProcessorTest.productName
              this.productPrice = productPrice
              this.webpageId = this@ContinuousViewProcessorTest.webpageId
            },
          )

          then("should not generate discount") {
            val inputStream: KStream<String, PagePingEvent> = mock()
            val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
            val windowedStream: SessionWindowedKStream<String, PagePingEvent> = mock()
            val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

            whenever(inputStream.groupByKey()).thenReturn(groupedStream)
            whenever(
              groupedStream.windowedBy(any<SessionWindows>()),
            ).thenReturn(windowedStream)
            whenever(
              windowedStream.aggregate(
                any<Initializer<ArrayList<PagePingEvent>>>(),
                any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                any<Merger<String, ArrayList<PagePingEvent>>>(),
                any(),
              ),
            ).thenReturn(aggregatedStream)
            whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
            whenever(suppressedStream.toStream()).thenReturn(finalStream)
            whenever(
              finalStream.peek(
                any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>(),
              ),
            )
              .thenReturn(peekedStream)
            whenever(
              peekedStream.flatMapValues(
                any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
              ),
            )
              .thenReturn(resultStream)

            val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>(
              "test-store",
            )

            processor.addProcessing(
              inputStream,
              windowSize,
              advanceSize,
              materialized,
            )

            verify(
              finalStream,
            ).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
            verify(
              peekedStream,
            ).flatMapValues(
              any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
            )

            val flatMapCaptor =
              argumentCaptor<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()
            verify(peekedStream).flatMapValues(flatMapCaptor.capture())

            val testKey = mock<Windowed<String>>()
            whenever(testKey.key()).thenReturn(userId)
            val result = flatMapCaptor.firstValue.apply(testKey, events)
            assert(result.toList().isEmpty()) {
              "Expected no discounts to be generated for insufficient ping events"
            }
          }
        }

        and("multiple products viewed continuously in the same window") {
          val events = ArrayList<PagePingEvent>()

          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime
              eventName = "product_view"
              this.userId = this@ContinuousViewProcessorTest.userId
              this.productId = "product1"
              this.productName = "Product 1"
              this.productPrice = 99.99
              this.webpageId = "webpage1"
            },
          )

          (1..4).forEach { i ->
            events.add(
              PagePingEvent().apply {
                collectorTimestamp = baseTime.plusSeconds(i * 15L)
                eventName = "page_ping"
                this.userId = this@ContinuousViewProcessorTest.userId
                this.webpageId = "webpage1"
              },
            )
          }

          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime.plusSeconds(100)
              eventName = "product_view"
              this.userId = this@ContinuousViewProcessorTest.userId
              this.productId = "product2"
              this.productName = "Product 2"
              this.productPrice = 199.99
              this.webpageId = "webpage2"
            },
          )

          (1..5).forEach { i ->
            events.add(
              PagePingEvent().apply {
                collectorTimestamp = baseTime.plusSeconds(100 + (i * 15L))
                eventName = "page_ping"
                this.userId = this@ContinuousViewProcessorTest.userId
                this.webpageId = "webpage2"
              },
            )
          }

          then("should generate discount only for product with most pings") {
            val inputStream: KStream<String, PagePingEvent> = mock()
            val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
            val windowedStream: SessionWindowedKStream<String, PagePingEvent> = mock()
            val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> =
              mock()
            val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

            whenever(inputStream.groupByKey()).thenReturn(groupedStream)
            whenever(
              groupedStream.windowedBy(any<SessionWindows>()),
            ).thenReturn(windowedStream)
            whenever(
              windowedStream.aggregate(
                any<Initializer<ArrayList<PagePingEvent>>>(),
                any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                any<Merger<String, ArrayList<PagePingEvent>>>(),
                any(),
              ),
            ).thenReturn(aggregatedStream)
            whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
            whenever(suppressedStream.toStream()).thenReturn(finalStream)
            whenever(
              finalStream.peek(
                any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>(),
              ),
            )
              .thenReturn(peekedStream)
            whenever(
              peekedStream.flatMapValues(
                any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
              ),
            )
              .thenReturn(resultStream)

            val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>(
              "test-store",
            )

            processor.addProcessing(
              inputStream,
              windowSize,
              advanceSize,
              materialized,
            )

            verify(
              finalStream,
            ).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
            verify(
              peekedStream,
            ).flatMapValues(
              any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>(),
            )

            val flatMapCaptor =
              argumentCaptor<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()
            verify(peekedStream).flatMapValues(flatMapCaptor.capture())

            val testKey = mock<Windowed<String>>()
            whenever(testKey.key()).thenReturn(userId)
            val result = flatMapCaptor.firstValue.apply(testKey, events).toList()

            assert(result.size == 1) { "Expected exactly one discount" }
            assert(
              result[0].productId == "product2",
            ) { "Expected discount for product with most pings" }
          }
        }
      }
    }
  }
}
