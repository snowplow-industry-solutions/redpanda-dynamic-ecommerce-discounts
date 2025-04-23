package com.example.serialization

import com.example.model.DiscountEvent
import com.fasterxml.jackson.core.JsonParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DiscountEventSerdeTest :
  BehaviorSpec({
    given("a DiscountEventSerde") {
      val serde = DiscountEventSerde()
      val serializer = serde.serializer()
      val deserializer = serde.deserializer()
      val fixedTimestamp = 1234567L

      `when`("handling a continuous view discount") {
        val discountEvent =
          DiscountEvent.createContinuousViewDiscount(
            "user123",
            "webpage456",
            90,
            0.1,
            fixedTimestamp,
          )

        then("should maintain data integrity through serialization and deserialization") {
          val serialized = serializer.serialize("test-topic", discountEvent)
          val deserialized = deserializer.deserialize("test-topic", serialized)
          deserialized shouldBe discountEvent
        }

        then("should produce the expected JSON format") {
          val serialized = serializer.serialize("test-topic", discountEvent)
          val jsonString = String(serialized)
          val expectedJson =
            """
            {
              "discount": {
                "rate": 0.1,
                "by_view_time": {
                  "duration_in_seconds": 90
                }
              },
              "user_id": "user123",
              "product_id": "webpage456",
              "generated_at": "1970-01-01T00:20:34.567Z"
            }
            """
              .trimIndent()
              .replace("\\s".toRegex(), "")

          jsonString shouldBe expectedJson
        }
      }

      `when`("handling a most viewed discount") {
        val discountEvent =
          DiscountEvent.createMostViewedDiscount(
            "user123",
            "webpage456",
            5,
            300,
            0.1,
            fixedTimestamp,
          )

        then("should maintain data integrity through serialization and deserialization") {
          val serialized = serializer.serialize("test-topic", discountEvent)
          val deserialized = deserializer.deserialize("test-topic", serialized)
          deserialized shouldBe discountEvent
        }

        then("should produce the expected JSON format") {
          val serialized = serializer.serialize("test-topic", discountEvent)
          val jsonString = String(serialized)
          val expectedJson =
            """
            {
              "discount": {
                "rate": 0.1,
                "by_number_of_views": {
                  "views": 5,
                  "duration_in_seconds": 300
                }
              },
              "user_id": "user123",
              "product_id": "webpage456",
              "generated_at": "1970-01-01T00:20:34.567Z"
            }
            """
              .trimIndent()
              .replace("\\s".toRegex(), "")

          jsonString shouldBe expectedJson
        }
      }

      `when`("deserializing null data") {
        then("should throw IllegalArgumentException") {
          shouldThrow<IllegalArgumentException> {
            deserializer.deserialize("test-topic", null)
          }
        }
      }

      `when`("deserializing invalid JSON") {
        then("should throw JsonParseException") {
          shouldThrow<JsonParseException> {
            deserializer.deserialize("test-topic", "invalid json".toByteArray())
          }
        }
      }
    }
  })
