package com.example.serialization;

import com.example.model.DiscountEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

@Slf4j
public class DiscountEventSerde implements Serde<DiscountEvent> {
  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {}

  @Override
  public void close() {}

  @Override
  public Serializer<DiscountEvent> serializer() {
    return new DiscountEventSerializer();
  }

  @Override
  public Deserializer<DiscountEvent> deserializer() {
    return new DiscountEventDeserializer();
  }

  private static class DiscountEventSerializer implements Serializer<DiscountEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    @SneakyThrows
    public byte[] serialize(String topic, DiscountEvent data) {
      return objectMapper.writeValueAsBytes(data);
    }

    @Override
    public void close() {}
  }

  private static class DiscountEventDeserializer implements Deserializer<DiscountEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    @SneakyThrows
    public DiscountEvent deserialize(String topic, byte[] data) {
      return objectMapper.readValue(data, DiscountEvent.class);
    }

    @Override
    public void close() {}
  }
}
