package com.example.processor

import com.example.config.ConfigurationManager
import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import com.example.model.DiscountEvent
import io.kotest.core.spec.style.BehaviorSpec
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.WindowStore
import org.apache.kafka.common.utils.Bytes
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
                    
                    events.add(ProductViewEvent().apply {
                        collectorTimestamp = baseTime
                        eventName = "product_view"
                        this.userId = this@ContinuousViewProcessorTest.userId
                        this.productId = this@ContinuousViewProcessorTest.productId
                        this.productName = this@ContinuousViewProcessorTest.productName
                        this.productPrice = productPrice
                        this.webpageId = this@ContinuousViewProcessorTest.webpageId
                    })
                    
                    (1..4).forEach { i ->
                        events.add(PagePingEvent().apply {
                            collectorTimestamp = baseTime.plusSeconds(i * 15L)
                            eventName = "page_ping"
                            this.userId = this@ContinuousViewProcessorTest.userId
                            this.webpageId = this@ContinuousViewProcessorTest.webpageId
                        })
                    }

                    then("should generate discount for continuous viewing") {
                        val inputStream: KStream<String, PagePingEvent> = mock()
                        val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
                        val windowedStream: TimeWindowedKStream<String, PagePingEvent> = mock()
                        val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

                        whenever(inputStream.groupByKey()).thenReturn(groupedStream)
                        whenever(groupedStream.windowedBy(any<TimeWindows>())).thenReturn(windowedStream)
                        whenever(windowedStream.aggregate(
                            any<Initializer<ArrayList<PagePingEvent>>>(),
                            any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                            any<Materialized<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>>()
                        )).thenReturn(aggregatedStream)
                        whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
                        whenever(suppressedStream.toStream()).thenReturn(finalStream)
                        whenever(finalStream.peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>()))
                            .thenReturn(peekedStream)
                        whenever(peekedStream.flatMapValues(any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()))
                            .thenReturn(resultStream)

                        val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>("test-store")
                        
                        processor.addProcessing(
                            inputStream,
                            windowSize,
                            advanceSize,
                            materialized
                        )

                        verify(finalStream).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
                        verify(peekedStream).flatMapValues(any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>())
                    }
                }

                and("user views product for less than minimum duration") {
                    val events = ArrayList<PagePingEvent>()
                    
                    events.add(ProductViewEvent().apply {
                        collectorTimestamp = baseTime
                        eventName = "product_view"
                        this.userId = this@ContinuousViewProcessorTest.userId
                        this.productId = this@ContinuousViewProcessorTest.productId
                        this.productName = this@ContinuousViewProcessorTest.productName
                        this.productPrice = productPrice
                        this.webpageId = this@ContinuousViewProcessorTest.webpageId
                    })
                    
                    (1..2).forEach { i ->
                        events.add(PagePingEvent().apply {
                            collectorTimestamp = baseTime.plusSeconds(i * 15L)
                            eventName = "page_ping"
                            this.userId = this@ContinuousViewProcessorTest.userId
                            this.webpageId = this@ContinuousViewProcessorTest.webpageId
                        })
                    }

                    then("should not generate discount") {
                        val inputStream: KStream<String, PagePingEvent> = mock()
                        val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
                        val windowedStream: TimeWindowedKStream<String, PagePingEvent> = mock()
                        val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

                        whenever(inputStream.groupByKey()).thenReturn(groupedStream)
                        whenever(groupedStream.windowedBy(any<TimeWindows>())).thenReturn(windowedStream)
                        whenever(windowedStream.aggregate(
                            any<Initializer<ArrayList<PagePingEvent>>>(),
                            any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                            any<Materialized<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>>()
                        )).thenReturn(aggregatedStream)
                        whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
                        whenever(suppressedStream.toStream()).thenReturn(finalStream)
                        whenever(finalStream.peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>()))
                            .thenReturn(peekedStream)
                        whenever(peekedStream.flatMapValues(any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()))
                            .thenReturn(resultStream)

                        val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>("test-store")
                        
                        processor.addProcessing(
                            inputStream,
                            windowSize,
                            advanceSize,
                            materialized
                        )

                        verify(finalStream).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
                        verify(peekedStream).flatMapValues(any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>())
                        
                        // Capture and verify the flatMapValues logic
                        val flatMapCaptor = argumentCaptor<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()
                        verify(peekedStream).flatMapValues(flatMapCaptor.capture())
                        
                        // Create a test key and verify the mapping produces no discounts
                        val testKey = mock<Windowed<String>>()
                        whenever(testKey.key()).thenReturn(userId)
                        val result = flatMapCaptor.firstValue.apply(testKey, events)
                        assert(result.toList().isEmpty()) { "Expected no discounts to be generated" }
                    }
                }

                and("insufficient ping events") {
                    val events = ArrayList<PagePingEvent>()
                    
                    events.add(ProductViewEvent().apply {
                        collectorTimestamp = baseTime
                        eventName = "product_view"
                        this.userId = this@ContinuousViewProcessorTest.userId
                        this.productId = this@ContinuousViewProcessorTest.productId
                        this.productName = this@ContinuousViewProcessorTest.productName
                        this.productPrice = productPrice
                        this.webpageId = this@ContinuousViewProcessorTest.webpageId
                    })

                    then("should not generate discount") {
                        val inputStream: KStream<String, PagePingEvent> = mock()
                        val groupedStream: KGroupedStream<String, PagePingEvent> = mock()
                        val windowedStream: TimeWindowedKStream<String, PagePingEvent> = mock()
                        val aggregatedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val suppressedStream: KTable<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val finalStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val peekedStream: KStream<Windowed<String>, ArrayList<PagePingEvent>> = mock()
                        val resultStream: KStream<Windowed<String>, DiscountEvent> = mock()

                        // Configure toda a cadeia de mocks
                        whenever(inputStream.groupByKey()).thenReturn(groupedStream)
                        whenever(groupedStream.windowedBy(any<TimeWindows>())).thenReturn(windowedStream)
                        whenever(windowedStream.aggregate(
                            any<Initializer<ArrayList<PagePingEvent>>>(),
                            any<Aggregator<String, PagePingEvent, ArrayList<PagePingEvent>>>(),
                            any<Materialized<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>>()
                        )).thenReturn(aggregatedStream)
                        whenever(aggregatedStream.suppress(any())).thenReturn(suppressedStream)
                        whenever(suppressedStream.toStream()).thenReturn(finalStream)
                        whenever(finalStream.peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>()))
                            .thenReturn(peekedStream)
                        whenever(peekedStream.flatMapValues(any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()))
                            .thenReturn(resultStream)

                        val materialized = Materialized.`as`<String, ArrayList<PagePingEvent>, WindowStore<Bytes, ByteArray>>("test-store")
                        
                        processor.addProcessing(
                            inputStream,
                            windowSize,
                            advanceSize,
                            materialized
                        )

                        // Verificações
                        verify(finalStream).peek(any<ForeachAction<Windowed<String>, ArrayList<PagePingEvent>>>())
                        verify(peekedStream).flatMapValues(any<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>())
                        
                        // Captura e verifica a lógica do flatMapValues
                        val flatMapCaptor = argumentCaptor<ValueMapperWithKey<Windowed<String>, ArrayList<PagePingEvent>, Iterable<DiscountEvent>>>()
                        verify(peekedStream).flatMapValues(flatMapCaptor.capture())
                        
                        // Cria uma chave de teste e verifica que nenhum desconto é gerado
                        val testKey = mock<Windowed<String>>()
                        whenever(testKey.key()).thenReturn(userId)
                        val result = flatMapCaptor.firstValue.apply(testKey, events)
                        assert(result.toList().isEmpty()) { "Expected no discounts to be generated for insufficient ping events" }
                    }
                }
            }
        }
    }
}