package com.example.serialization;

import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventDeserializationHelper {
  private EventDeserializationHelper() {}

  public static PagePingEvent deserializeEvent(ObjectMapper mapper, JsonNode node) {
    try {
      String eventName = node.path("event_name").asText();
      if ("product_view".equals(eventName)) {
        return mapper.treeToValue(node, ProductViewEvent.class);
      } else {
        return mapper.treeToValue(node, PagePingEvent.class);
      }
    } catch (Exception e) {
      log.error("Failed to deserialize event", e);
      throw new EventDeserializationException("Failed to deserialize event", e);
    }
  }
}
