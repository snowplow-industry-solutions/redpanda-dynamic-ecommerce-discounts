package com.example.serialization;

import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

@Slf4j
public class EventSerde implements Serde<PagePingEvent> {
  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .registerModule(new CustomInstantModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

  @Override
  public void close() {}

  @Override
  public Serializer<PagePingEvent> serializer() {
    return new EventSerializer();
  }

  @Override
  public Deserializer<PagePingEvent> deserializer() {
    return new EventDeserializer();
  }

  private static class EventSerializer implements Serializer<PagePingEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    @SneakyThrows
    public byte[] serialize(String topic, PagePingEvent data) {
      return objectMapper.writeValueAsBytes(data);
    }

    @Override
    public void close() {}
  }

  private static class EventDeserializer implements Deserializer<PagePingEvent> {
    @Override
    public PagePingEvent deserialize(String topic, byte[] data) {
      if (data == null) {
        return null;
      }

      try {
        String json = new String(data, StandardCharsets.UTF_8);
        log.debug("Attempting to deserialize event: {}", json);
        JsonNode node = objectMapper.readTree(json);

        try {
          validateRequiredFields(node);
        } catch (RuntimeException e) {
          throw e;
        }

        String eventName = node.path("event_name").asText();
        log.debug("Deserializing event_name: {}", eventName);

        if ("product_view".equals(eventName)) {
          return objectMapper.treeToValue(node, ProductViewEvent.class);
        } else {
          return objectMapper.treeToValue(node, PagePingEvent.class);
        }
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        log.error("Failed to deserialize event: {}", e.getMessage(), e);
        throw new EventDeserializationException("Failed to deserialize event", e);
      }
    }

    private void validateRequiredFields(JsonNode node) {
      if (!node.has("user_id") || node.get("user_id").isNull()) {
        log.error("Missing required field: user_id");
        throw new EventDeserializationException("Missing required field: user_id", null);
      }
      if (!node.has("webpage_id") || node.get("webpage_id").isNull()) {
        log.error("Missing required field: webpage_id");
        throw new EventDeserializationException("Missing required field: webpage_id", null);
      }
      if (!node.has("collector_tstamp") || node.get("collector_tstamp").isNull()) {
        log.error("Missing required field: collector_tstamp");
        throw new EventDeserializationException("Missing required field: collector_tstamp", null);
      }

      String eventName = node.path("event_name").asText();
      if ("product_view".equals(eventName)) {
        if (!node.has("product_id") || node.get("product_id").isNull()) {
          log.error("Missing required field for product_view: product_id");
          throw new EventDeserializationException(
              "Missing required field for product_view: product_id", null);
        }
      }
    }

    @Override
    public void close() {}
  }
}
