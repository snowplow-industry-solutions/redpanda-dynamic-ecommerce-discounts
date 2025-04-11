package com.example.serialization

import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.io.IOException

class EventDeserializationSchemaTest : BehaviorSpec({
    val deserializer = EventDeserializationSchema()

    given("an EventDeserializationSchema") {
        `when`("deserializing a page_ping event") {
            val json = """
                {
                    "collector_tstamp": "2025-04-04T07:05:12.130Z",
                    "event_name": "page_ping",
                    "user_id": "user123",
                    "webpage_id": "page456"
                }
            """.trimIndent()

            then("should correctly deserialize all fields") {
                val event = deserializer.deserialize(json.toByteArray(StandardCharsets.UTF_8))
                
                event shouldBe PagePingEvent().apply {
                    collectorTimestamp = Instant.parse("2025-04-04T07:05:12.130Z")
                    eventName = "page_ping"
                    userId = "user123"
                    webpageId = "page456"
                }
            }
        }

        `when`("deserializing a product_view event") {
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
                val event = deserializer.deserialize(json.toByteArray(StandardCharsets.UTF_8))
                
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

        `when`("deserializing a product_view event without optional fields") {
            val json = """
                {
                    "collector_tstamp": "2025-04-04T07:05:00.119Z",
                    "event_name": "product_view",
                    "user_id": "user123",
                    "product_id": "prod789"
                }
            """.trimIndent()

            then("should deserialize with null optional fields") {
                val event = deserializer.deserialize(json.toByteArray(StandardCharsets.UTF_8))
                
                event shouldBe ProductViewEvent().apply {
                    collectorTimestamp = Instant.parse("2025-04-04T07:05:00.119Z")
                    eventName = "product_view"
                    userId = "user123"
                    productId = "prod789"
                    productName = null
                    productPrice = null
                    webpageId = null
                }
            }
        }

        `when`("deserializing invalid JSON") {
            val invalidJson = "{ invalid json }"

            then("should throw an exception") {
                kotlin.runCatching {
                    deserializer.deserialize(invalidJson.toByteArray(StandardCharsets.UTF_8))
                }.isFailure shouldBe true
            }
        }

        `when`("deserializing event with missing required fields") {
            val incompleteJson = """
                {
                    "event_name": "product_view"
                }
            """.trimIndent()

            then("should throw an exception") {
                shouldThrow<IOException> {
                    deserializer.deserialize(incompleteJson.toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
    }
})