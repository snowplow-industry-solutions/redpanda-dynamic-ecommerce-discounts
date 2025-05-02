package com.example.processor

import com.example.config.ConfigurationManager
import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Paths
import java.time.Instant
import java.util.Properties

private const val WINDOW_DURATION_SECONDS = 300
private const val DELAY_TO_FIRST_PING_SECONDS = 10L
private const val PING_INTERVAL_SECONDS = 10L
private const val MIN_PINGS_FOR_DISCOUNT = 3
private const val DISCOUNT_RATE = 0.1

private const val SUFFICIENT_PINGS = MIN_PINGS_FOR_DISCOUNT + 1
private const val INSUFFICIENT_PINGS = MIN_PINGS_FOR_DISCOUNT - 1

class ProcessorHelperTest :
  BehaviorSpec({
    val userId = "user123"
    val productId = "prod456"
    val webpageId = "page789"
    val productName = "Test Product"
    val productPrice = 99.99
    val baseTime = Instant.parse("2024-01-01T10:00:00Z")

    val objectMapper =
      ObjectMapper().apply {
        registerModule(JavaTimeModule())
        registerKotlinModule()
      }

    fun getResourceFile(testName: String): File {
      val resourcesPath = Paths.get("src", "test", "resources", "processor-helper-test")
      resourcesPath.toFile().mkdirs()
      return resourcesPath.resolve("$testName.json").toFile()
    }

    fun loadOrSaveEvents(
      testName: String,
      generateEvents: () -> List<PagePingEvent>,
    ): List<PagePingEvent> {
      val file = getResourceFile(testName)
      val logger = org.slf4j.LoggerFactory.getLogger(ProcessorHelperTest::class.java)
      val deserializer = EventDeserializer(objectMapper)

      return if (file.exists()) {
        logger.info("Loading events from file: ${file.absolutePath}")
        deserializer.deserializeEvents(file.readText())
      } else {
        logger.info("Generating new events and saving to file: ${file.absolutePath}")
        val events = generateEvents()
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, events)
        events
      }
    }

    given("a processor helper") {
      lateinit var processor: ProcessorHelper

      beforeTest {
        val testProperties =
          Properties().apply {
            setProperty("processor.continuous-view.enabled", "true")
            setProperty(
              "processor.window.duration.seconds",
              WINDOW_DURATION_SECONDS.toString(),
            )
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
        processor = ProcessorHelper()
      }

      `when`("calculating product view summaries") {
        and("there are multiple products with different ping counts") {
          val events = mutableListOf<PagePingEvent>()

          beforeTest {
            events.addAll(
              loadOrSaveEvents("all.multiple-products-different") {
                buildList {
                  add(createProductViewEvent(baseTime, "prod1", "page1"))
                  repeat(5) { i ->
                    add(
                      createPagePingEvent(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                        "page1",
                      ),
                    )
                  }
                  add(createProductViewEvent(baseTime, "prod2", "page2"))
                  repeat(3) { i ->
                    add(
                      createPagePingEvent(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                        "page2",
                      ),
                    )
                  }
                  repeat(4) { i ->
                    add(
                      createPagePingEvent(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                        "page3",
                      ),
                    )
                  }
                }
              },
            )
          }

          then(
            "should correctly summarize all page pings, including those without product views",
          ) {
            val summaries = processor.calculateProductViewSummaries(events)

            summaries shouldHaveSize 2

            val prod1Summary = summaries["page1"]
            prod1Summary shouldNotBe null
            prod1Summary?.pingCount shouldBe 5
            prod1Summary?.productView?.isPresent shouldBe true

            val prod2Summary = summaries["page2"]
            prod2Summary shouldNotBe null
            prod2Summary?.pingCount shouldBe 3
            prod2Summary?.productView?.isPresent shouldBe true

            summaries["page3"] shouldBe null

            events.count { it.eventName == "page_ping" } shouldBe 12
            events.count { it is ProductViewEvent } shouldBe 2
          }
        }

        and("there are only page pings without any product views") {
          val events = mutableListOf<PagePingEvent>()

          beforeTest {
            events.addAll(
              loadOrSaveEvents("page_ping.only") {
                buildList {
                  repeat(5) { i ->
                    add(
                      createPagePingEvent(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                        "page1",
                      ),
                    )
                  }
                  repeat(3) { i ->
                    add(
                      createPagePingEvent(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                        "page2",
                      ),
                    )
                  }
                }
              },
            )
          }

          then("should return empty map but correctly count pings") {
            val summaries = processor.calculateProductViewSummaries(events)

            summaries shouldHaveSize 0

            events.count { it.eventName == "page_ping" } shouldBe 8
            events.count { it is ProductViewEvent } shouldBe 0
          }
        }
      }

      `when`("processing events with sufficient continuous views") {
        val events = mutableListOf<PagePingEvent>()

        beforeTest {
          events.addAll(
            loadOrSaveEvents("all.sufficient-continuous-views") {
              buildList {
                add(
                  ProductViewEvent().apply {
                    setCollectorTimestamp(baseTime)
                    setEventName("product_view")
                    setUserId(userId)
                    setProductId(productId)
                    setProductName(productName)
                    setProductPrice(productPrice)
                    setWebpageId(webpageId)
                  },
                )

                (1..SUFFICIENT_PINGS).forEach { i ->
                  add(
                    PagePingEvent().apply {
                      setCollectorTimestamp(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                      )
                      setEventName("page_ping")
                      setUserId(userId)
                      setWebpageId(webpageId)
                    },
                  )
                }
              }
            },
          )
        }

        then("should generate discount") {
          val result = processor.processEvents(userId, events)
          result.isPresent shouldBe true

          val discount = result.get()
          discount.userId shouldBe userId
          discount.productId shouldBe productId
          discount.discount.rate shouldBe DISCOUNT_RATE
          discount.discount.byViewTime?.durationInSeconds shouldBe
            (DELAY_TO_FIRST_PING_SECONDS + SUFFICIENT_PINGS * PING_INTERVAL_SECONDS)
        }
      }

      `when`("processing events with insufficient pings") {
        val events = mutableListOf<PagePingEvent>()

        beforeTest {
          events.addAll(
            loadOrSaveEvents("all.insufficient") {
              buildList {
                add(
                  ProductViewEvent().apply {
                    setCollectorTimestamp(baseTime)
                    setEventName("product_view")
                    setUserId(userId)
                    setProductId(productId)
                    setProductName(productName)
                    setProductPrice(productPrice)
                    setWebpageId(webpageId)
                  },
                )

                (1..INSUFFICIENT_PINGS).forEach { i ->
                  add(
                    PagePingEvent().apply {
                      setCollectorTimestamp(
                        baseTime.plusSeconds(i * PING_INTERVAL_SECONDS),
                      )
                      setEventName("page_ping")
                      setUserId(userId)
                      setWebpageId(webpageId)
                    },
                  )
                }
              }
            },
          )
        }

        then("should not generate discount") {
          val result = processor.processEvents(userId, events)
          result.isPresent shouldBe false
        }
      }

      `when`("processing only page ping events without product view") {
        val events = mutableListOf<PagePingEvent>()

        beforeTest {
          (1..SUFFICIENT_PINGS).forEach { i ->
            val pingEvent = PagePingEvent()
            pingEvent.collectorTimestamp = baseTime.plusSeconds(i * PING_INTERVAL_SECONDS)
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
  }) {
  companion object {
    private fun createProductViewEvent(
      timestamp: Instant,
      productId: String,
      webpageId: String,
    ): ProductViewEvent =
      ProductViewEvent().apply {
        collectorTimestamp = timestamp
        this.productId = productId
        this.webpageId = webpageId
        eventName = "product_view"
      }

    private fun createPagePingEvent(timestamp: Instant, webpageId: String): PagePingEvent =
      PagePingEvent().apply {
        collectorTimestamp = timestamp
        this.webpageId = webpageId
        eventName = "page_ping"
      }
  }
}
