package com.example.processor

import com.example.config.ConfigurationManager
import com.example.model.DiscountEvent
import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import com.example.processor.ContinuousViewProcessor.WindowState
import com.example.serialization.DiscountEventSerde
import com.example.serialization.PagePingEventSerde
import com.example.serialization.WindowStateSerde
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.Stores
import org.apache.kafka.streams.state.WindowStore
import java.time.Instant
import java.util.Properties

@Ignored
class ContinuousViewProcessorTest : BehaviorSpec({
  val userId = "user1"
  val productId1 = "product1"
  val productId2 = "product2"
  val webpageId1 = "webpage1"
  val webpageId2 = "webpage2"
  val baseTime = Instant.parse("2024-01-01T10:00:00Z")

  given("a continuous view processor with test topology") {
    lateinit var testDriver: TopologyTestDriver
    lateinit var inputTopic: TestInputTopic<String, PagePingEvent>
    lateinit var outputTopic: TestOutputTopic<String, DiscountEvent>

    beforeTest {
      val props = Properties().apply {

        setProperty("processor.continuous-view.enabled", "true")
        setProperty("processor.window.duration.seconds", "300")
        setProperty("processor.continuous-view.ping-interval.seconds", "10")
        setProperty("processor.continuous-view.min-pings-for-discount", "3")
        setProperty("processor.discount-rate", "0.1")

        setProperty("kafka.input.topic", "input-topic")
        setProperty("kafka.output.topic", "output-topic")

        setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "test")
        setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234")
      }
      ConfigurationManager.getInstance(props)

      val builder = StreamsBuilder()
      val processor = ContinuousViewProcessor()

      val storeBuilder = Stores.windowStoreBuilder(
        Stores.persistentWindowStore(
          "continuous-view-store",
          java.time.Duration.ofDays(1),
          java.time.Duration.ofMinutes(5),
          false,
        ),
        Serdes.String(),
        WindowStateSerde(),
      )
      builder.addStateStore(storeBuilder)

      val inputStream = builder.stream<String, PagePingEvent>("input-topic")
      processor.addProcessing(
        inputStream,
        Materialized.`as`<String, WindowState, WindowStore<Bytes, ByteArray>>(
          "continuous-view-store",
        )
          .withKeySerde(Serdes.String())
          .withValueSerde(WindowStateSerde()),
      )

      testDriver = TopologyTestDriver(builder.build(), props)
      inputTopic = testDriver.createInputTopic(
        "input-topic",
        Serdes.String().serializer(),
        PagePingEventSerde().serializer(),
      )
      outputTopic = testDriver.createOutputTopic(
        "output-topic",
        Serdes.String().deserializer(),
        DiscountEventSerde().deserializer(),
      )
    }

    afterTest {
      testDriver.close()
    }

    `when`("receiving multiple qualifying view sessions in same window") {
      beforeTest {

        inputTopic.pipeInput(
          userId,
          ProductViewEvent().apply {
            collectorTimestamp = baseTime
            eventName = "product_view"
            this.userId = userId
            this.productId = productId1
            this.webpageId = webpageId1
            this.productPrice = 99.99
          },
        )

        repeat(4) { i ->
          inputTopic.pipeInput(
            userId,
            PagePingEvent().apply {
              collectorTimestamp = baseTime.plusSeconds(i * 10L)
              eventName = "page_ping"
              this.userId = userId
              this.webpageId = webpageId1
            },
          )
        }

        inputTopic.pipeInput(
          userId,
          ProductViewEvent().apply {
            collectorTimestamp = baseTime.plusSeconds(60)
            eventName = "product_view"
            this.userId = userId
            this.productId = productId2
            this.webpageId = webpageId2
            this.productPrice = 149.99
          },
        )

        repeat(4) { i ->
          inputTopic.pipeInput(
            userId,
            PagePingEvent().apply {
              collectorTimestamp = baseTime.plusSeconds(60 + i * 10L)
              eventName = "page_ping"
              this.userId = userId
              this.webpageId = webpageId2
            },
          )
        }
      }

      then("should generate only one discount for first qualifying product") {
        val discounts = outputTopic.readValuesToList()
        discounts.size shouldBe 1

        val discount = discounts.first()
        discount.userId shouldBe userId
        discount.productId shouldBe productId1
        discount.discount.rate shouldBe 0.1
      }
    }

    `when`("receiving qualifying views in different windows") {
      beforeTest {

        inputTopic.pipeInput(
          userId,
          ProductViewEvent().apply {
            collectorTimestamp = baseTime
            eventName = "product_view"
            this.userId = userId
            this.productId = productId1
            this.webpageId = webpageId1
            this.productPrice = 99.99
          },
        )

        repeat(4) { i ->
          inputTopic.pipeInput(
            userId,
            PagePingEvent().apply {
              collectorTimestamp = baseTime.plusSeconds(i * 10L)
              eventName = "page_ping"
              this.userId = userId
              this.webpageId = webpageId1
            },
          )
        }

        val secondWindowTime = baseTime.plusSeconds(360)
        inputTopic.pipeInput(
          userId,
          ProductViewEvent().apply {
            collectorTimestamp = secondWindowTime
            eventName = "product_view"
            this.userId = userId
            this.productId = productId2
            this.webpageId = webpageId2
            this.productPrice = 149.99
          },
        )

        repeat(4) { i ->
          inputTopic.pipeInput(
            userId,
            PagePingEvent().apply {
              collectorTimestamp = secondWindowTime.plusSeconds(i * 10L)
              eventName = "page_ping"
              this.userId = userId
              this.webpageId = webpageId2
            },
          )
        }
      }

      then("should generate discounts for both products") {
        val discounts = outputTopic.readValuesToList()
        discounts.size shouldBe 2

        discounts[0].productId shouldBe productId1
        discounts[1].productId shouldBe productId2
      }
    }
  }
}) {
  val userId = "user1"
  val productId = "product1"
  val webpageId = "webpage1"
  val productName = "Test Product"
  val productPrice = 99.99

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

        and("user views product for less than minimum duration") {
          val events = ArrayList<PagePingEvent>()
          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime
              eventName = "product_view"
              this.userId = userId
              this.productId = productId
              this.productName = productName
              this.productPrice = productPrice
              this.webpageId = webpageId
            },
          )

          (1..2).forEach { i ->
            events.add(
              PagePingEvent().apply {
                collectorTimestamp = baseTime.plusSeconds(i * 15L)
                eventName = "page_ping"
                this.userId = userId
                this.webpageId = webpageId
              },
            )
          }

          then("should not generate discount") {
            val result = processor.processEvents(userId, events)
            assert(result.isEmpty()) {
              "Expected no discounts to be generated for insufficient viewing time"
            }
          }
        }

        and("user views product continuously meeting minimum duration") {
          val events = ArrayList<PagePingEvent>()
          events.add(
            ProductViewEvent().apply {
              collectorTimestamp = baseTime
              eventName = "product_view"
              this.userId = userId
              this.productId = productId
              this.productName = productName
              this.productPrice = productPrice
              this.webpageId = webpageId
            },
          )

          (1..4).forEach { i ->
            events.add(
              PagePingEvent().apply {
                collectorTimestamp = baseTime.plusSeconds(i * 15L)
                eventName = "page_ping"
                this.userId = userId
                this.webpageId = webpageId
              },
            )
          }

          then("should generate discount for continuous viewing") {
            val result = processor.processEvents(userId, events)
            assert(result.isPresent) {
              "Expected discount to be generated for sufficient viewing time"
            }

            val discount = result.get()
            assert(discount.productId == productId)
            assert(discount.userId == userId)
          }
        }
      }
    }
  }
}
