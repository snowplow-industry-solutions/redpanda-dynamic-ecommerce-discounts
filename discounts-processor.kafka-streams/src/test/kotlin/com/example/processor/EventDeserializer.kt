package com.example.processor

import com.example.model.PagePingEvent
import com.example.model.ProductViewEvent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class EventDeserializer(private val objectMapper: ObjectMapper) {
  fun deserializeEvents(json: String): List<PagePingEvent> {
    val nodes = objectMapper.readTree(json)
    return nodes.map { node -> deserializeEvent(node) }
  }

  private fun deserializeEvent(node: JsonNode): PagePingEvent {
    return when (node.get("event_name").asText()) {
      "product_view" -> objectMapper.treeToValue(node, ProductViewEvent::class.java)
      "page_ping" -> objectMapper.treeToValue(node, PagePingEvent::class.java)
      else ->
        throw IllegalArgumentException(
          "Unknown event type: ${node.get("event_name").asText()}",
        )
    }
  }
}
