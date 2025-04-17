package com.example.serialization;

import com.example.model.PagePingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class PagePingEventSerde implements Serde<PagePingEvent> {
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Serializer<PagePingEvent> serializer() {
    return new Serializer<>() {
      @Override
      public byte[] serialize(String topic, PagePingEvent data) {
        try {
          return mapper.writeValueAsBytes(data);
        } catch (Exception e) {
          throw new RuntimeException("Error serializing PagePingEvent", e);
        }
      }
    };
  }

  @Override
  public Deserializer<PagePingEvent> deserializer() {
    return new Deserializer<>() {
      @Override
      public PagePingEvent deserialize(String topic, byte[] data) {
        try {
          return mapper.readValue(data, PagePingEvent.class);
        } catch (Exception e) {
          throw new RuntimeException("Error deserializing PagePingEvent", e);
        }
      }
    };
  }
}
