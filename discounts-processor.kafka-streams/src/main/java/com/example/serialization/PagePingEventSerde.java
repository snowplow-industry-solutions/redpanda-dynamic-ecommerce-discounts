package com.example.serialization;

import com.example.model.PagePingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class PagePingEventSerde implements Serde<PagePingEvent> {
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Serializer<PagePingEvent> serializer() {
    return new Serializer<>() {
      @Override
      @SneakyThrows
      public byte[] serialize(String topic, PagePingEvent data) {
        return mapper.writeValueAsBytes(data);
      }
    };
  }

  @Override
  public Deserializer<PagePingEvent> deserializer() {
    return new Deserializer<>() {
      @Override
      @SneakyThrows
      public PagePingEvent deserialize(String topic, byte[] data) {
        return mapper.readValue(data, PagePingEvent.class);
      }
    };
  }
}
