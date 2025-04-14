package com.example.serialization

import com.example.model.DiscountEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.JsonParseException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.assertions.throwables.shouldThrow
import java.nio.charset.StandardCharsets

class DiscountEventSerdeTest : BehaviorSpec({
    val objectMapper = ObjectMapper()
    val serde = DiscountEventSerde()
    val serializer = serde.serializer()
    val deserializer = serde.deserializer()

    given("a DiscountEventSerde") {
        `when`("handling a continuous view discount") {
            val discountEvent = DiscountEvent.createContinuousViewDiscount(
                "user123",
                "webpage456",
                90,
                0.1
            )

            then("should maintain data integrity through serialization and deserialization") {
                val serialized = serializer.serialize("test-topic", discountEvent)
                val deserialized = deserializer.deserialize("test-topic", serialized)
                
                deserialized shouldBe discountEvent
            }

            then("should produce the expected JSON format") {
                val actualJson = String(serializer.serialize("test-topic", discountEvent), StandardCharsets.UTF_8)
                
                val expectedJson = """
                    {
                      "user_id": "user123",
                      "product_id": "webpage456",
                      "discount": {
                        "rate": 0.1,
                        "by_view_time": {
                          "duration_in_seconds": 90
                        }
                      }
                    }
                """.trimIndent()

                val expectedNode = objectMapper.readTree(expectedJson)
                val actualNode = objectMapper.readTree(actualJson)
                
                actualNode shouldBe expectedNode
            }
        }

        `when`("handling a most viewed discount") {
            val discountEvent = DiscountEvent.createMostViewedDiscount(
                "user123",
                "webpage456",
                5,
                300,
                0.1
            )

            then("should maintain data integrity through serialization and deserialization") {
                val serialized = serializer.serialize("test-topic", discountEvent)
                val deserialized = deserializer.deserialize("test-topic", serialized)
                
                deserialized shouldBe discountEvent
            }

            then("should produce the expected JSON format") {
                val actualJson = String(serializer.serialize("test-topic", discountEvent), StandardCharsets.UTF_8)
                
                val expectedJson = """
                    {
                      "user_id": "user123",
                      "product_id": "webpage456",
                      "discount": {
                        "rate": 0.1,
                        "by_number_of_views": {
                          "views": 5,
                          "duration_in_seconds": 300
                        }
                      }
                    }
                """.trimIndent()

                val expectedNode = objectMapper.readTree(expectedJson)
                val actualNode = objectMapper.readTree(actualJson)
                
                actualNode shouldBe expectedNode
            }
        }

        `when`("deserializing null data") {
            then("should return null") {
                deserializer.deserialize("test-topic", null) shouldBe null
            }
        }

        `when`("deserializing invalid JSON") {
            val invalidJson = "{ invalid json }"

            then("should throw RuntimeException with JsonParseException as cause") {
                val exception = shouldThrow<RuntimeException> {
                    deserializer.deserialize("test-topic", invalidJson.toByteArray(StandardCharsets.UTF_8))
                }
                exception.cause!!.shouldBeInstanceOf<JsonParseException>()
            }
        }
    }
})