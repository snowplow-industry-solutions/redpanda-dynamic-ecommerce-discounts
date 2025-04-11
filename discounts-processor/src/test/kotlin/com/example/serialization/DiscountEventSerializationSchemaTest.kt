package com.example.serialization

import com.example.model.DiscountEvent
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.nio.charset.StandardCharsets

class DiscountEventSerializationSchemaTest : BehaviorSpec({
    val objectMapper = ObjectMapper()
    val serializer = DiscountEventSerializationSchema()

    given("a DiscountEventSerializationSchema") {
        `when`("serializing a continuous view discount") {
            val discountEvent = DiscountEvent.createContinuousViewDiscount(
                "user123",
                "webpage456",
                90
            )

            then("should produce the expected JSON format") {
                val actualJson = String(serializer.serialize(discountEvent), StandardCharsets.UTF_8)
                
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

        `when`("serializing a most viewed discount") {
            val discountEvent = DiscountEvent.createMostViewedDiscount(
                "user123",
                "webpage456",
                5,
                300
            )

            then("should produce the expected JSON format") {
                val actualJson = String(serializer.serialize(discountEvent), StandardCharsets.UTF_8)
                
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
    }
})