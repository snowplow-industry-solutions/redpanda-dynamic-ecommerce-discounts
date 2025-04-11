package com.example.serialization;

import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class EventDeserializationSchema implements DeserializationSchema<PagePingEvent> {
  private static final Logger log = LoggerFactory.getLogger(EventDeserializationSchema.class);
  private static final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());

  @Override
  public PagePingEvent deserialize(byte[] message) throws IOException {
    String json = new String(message, StandardCharsets.UTF_8);
    JsonNode node = objectMapper.readTree(json);

    validateRequiredFields(node);

    String eventName = node.path("event_name").asText();

    try {
      if ("product_view".equals(eventName)) {
        return objectMapper.treeToValue(node, ProductViewEvent.class);
      } else {
        return objectMapper.treeToValue(node, PagePingEvent.class);
      }
    } catch (Exception e) {
      log.error("Failed to deserialize event: {}", json, e);
      throw new IOException("Failed to deserialize event", e);
    }
  }

  private void validateRequiredFields(JsonNode node) throws IOException {
    if (!node.has("collector_tstamp") || node.get("collector_tstamp").isNull()) {
      throw new IOException("Missing required field: collector_tstamp");
    }
    if (!node.has("event_name") || node.get("event_name").isNull()) {
      throw new IOException("Missing required field: event_name");
    }
    if (!node.has("user_id") || node.get("user_id").isNull()) {
      throw new IOException("Missing required field: user_id");
    }

    String eventName = node.path("event_name").asText();
    if ("product_view".equals(eventName)) {
      if (!node.has("product_id") || node.get("product_id").isNull()) {
        throw new IOException("Missing required field for product_view: product_id");
      }
    }
  }

  @Override
  public boolean isEndOfStream(PagePingEvent nextElement) {
    return false;
  }

  @Override
  public TypeInformation<PagePingEvent> getProducedType() {
    return TypeInformation.of(PagePingEvent.class);
  }
}
