package com.example.serialization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class CustomInstantModuleTest : FunSpec({

  val objectMapper = ObjectMapper()
    .registerModule(CustomInstantModule())
    .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule())

  data class Event @JsonCreator constructor(
    @JsonProperty("timestamp") val timestamp: Instant,
  )

  test("should deserialize timestamp with 3 digits millis") {
    val json = """{"timestamp":"2024-06-07T12:34:56.123Z"}"""
    val event = objectMapper.readValue(json, Event::class.java)
    event.timestamp shouldBe Instant.parse("2024-06-07T12:34:56.123Z")
  }

  test("should deserialize timestamp with 2 digits millis") {
    val json = """{"timestamp":"2024-06-07T12:34:56.12Z"}"""
    val event = objectMapper.readValue(json, Event::class.java)
    event.timestamp shouldBe Instant.parse("2024-06-07T12:34:56.120Z")
  }

  test("should deserialize timestamp with 1 digit millis") {
    val json = """{"timestamp":"2024-06-07T12:34:56.1Z"}"""
    val event = objectMapper.readValue(json, Event::class.java)
    event.timestamp shouldBe Instant.parse("2024-06-07T12:34:56.100Z")
  }

  test("should deserialize timestamp without millis") {
    val json = """{"timestamp":"2024-06-07T12:34:56Z"}"""
    val event = objectMapper.readValue(json, Event::class.java)
    event.timestamp shouldBe Instant.parse("2024-06-07T12:34:56Z")
  }
})
