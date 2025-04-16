package com.example.serialization

import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import com.fasterxml.jackson.core.JsonParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.charset.StandardCharsets
import java.time.Instant

class EventSerdeTest : BehaviorSpec({
  val serde = EventSerde()
  val serializer = serde.serializer()
  val deserializer = serde.deserializer()

  given("an EventSerde") {
    `when`("serializing and deserializing a page_ping event") {
      val originalEvent = PagePingEvent().apply {
        collectorTimestamp = Instant.parse("2025-04-04T07:05:12.130Z")
        eventName = "page_ping"
        userId = "user123"
        webpageId = "page456"
      }

      then("should maintain data integrity through the process") {
        val serialized = serializer.serialize("test-topic", originalEvent)
        val deserialized = deserializer.deserialize("test-topic", serialized)

        deserialized shouldBe originalEvent
      }
    }

    `when`("serializing and deserializing a product_view event") {
      val originalEvent = ProductViewEvent().apply {
        collectorTimestamp = Instant.parse("2025-04-04T07:05:00.119Z")
        eventName = "product_view"
        userId = "user123"
        productId = "prod789"
        productName = "SP Flex Runner 2"
        productPrice = 42.99
        webpageId = "page456"
      }

      then("should maintain data integrity through the process") {
        val serialized = serializer.serialize("test-topic", originalEvent)
        val deserialized = deserializer.deserialize("test-topic", serialized)

        deserialized shouldBe originalEvent
      }
    }

    `when`("deserializing a page_ping event from JSON") {
      val json = """
                {
                    "collector_tstamp": "2025-04-04T07:05:12.130Z",
                    "event_name": "page_ping",
                    "user_id": "user123",
                    "webpage_id": "page456"
                }
      """.trimIndent()

      then("should correctly deserialize all fields") {
        val event = deserializer.deserialize(
          "test-topic",
          json.toByteArray(StandardCharsets.UTF_8),
        )

        event shouldBe PagePingEvent().apply {
          collectorTimestamp = Instant.parse("2025-04-04T07:05:12.130Z")
          eventName = "page_ping"
          userId = "user123"
          webpageId = "page456"
        }
      }
    }

    `when`("deserializing a product_view event from JSON") {
      val json = """
                {
                    "collector_tstamp": "2025-04-04T07:05:00.119Z",
                    "event_name": "product_view",
                    "user_id": "user123",
                    "product_id": "prod789",
                    "product_name": "SP Flex Runner 2",
                    "product_price": 42.99,
                    "webpage_id": "page456"
                }
      """.trimIndent()

      then("should correctly deserialize all fields") {
        val event = deserializer.deserialize(
          "test-topic",
          json.toByteArray(StandardCharsets.UTF_8),
        )

        event shouldBe ProductViewEvent().apply {
          collectorTimestamp = Instant.parse("2025-04-04T07:05:00.119Z")
          eventName = "product_view"
          userId = "user123"
          productId = "prod789"
          productName = "SP Flex Runner 2"
          productPrice = 42.99
          webpageId = "page456"
        }
      }
    }

    `when`("deserializing invalid JSON") {
      val invalidJson = "{ invalid json }"

      then("should throw RuntimeException with JsonParseException as cause") {
        val exception = shouldThrow<RuntimeException> {
          deserializer.deserialize(
            "test-topic",
            invalidJson.toByteArray(StandardCharsets.UTF_8),
          )
        }
        exception.cause!!.shouldBeInstanceOf<JsonParseException>()
      }
    }

    `when`("deserializing event with missing required fields") {
      val incompleteJson = """
                {
                    "event_name": "product_view"
                }
      """.trimIndent()

      then("should throw RuntimeException with appropriate message") {
        val exception = shouldThrow<RuntimeException> {
          deserializer.deserialize(
            "test-topic",
            incompleteJson.toByteArray(StandardCharsets.UTF_8),
          )
        }
        exception.message shouldBe "Missing required field: user_id"
      }
    }

    `when`("deserializing null data") {
      then("should return null") {
        deserializer.deserialize("test-topic", null) shouldBe null
      }
    }
  }
})
