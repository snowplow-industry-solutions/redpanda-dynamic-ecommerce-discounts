package com.example.processor

import com.example.model.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("Usage: summarizeViews <output-file>")
    System.exit(1)
  }

  val outputFile = args[0]

  val mapper =
    ObjectMapper()
      .registerKotlinModule()
      .registerModule(JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  val events =
    generateSequence(::readLine)
      .map { line ->
        if (line.contains("\"event_name\":\"product_view\"")) {
          mapper.readValue(line, ProductViewEvent::class.java)
        } else {
          mapper.readValue(line, PagePingEvent::class.java)
        }
      }
      .toList()

  val processor = ProcessorHelper()
  val summary = processor.summarizeViews(events)

  File(outputFile).writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary))
}
