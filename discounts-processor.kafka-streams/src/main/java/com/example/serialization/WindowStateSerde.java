package com.example.serialization;

import com.example.processor.ContinuousViewProcessor.WindowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class WindowStateSerde implements Serde<WindowState> {
  private final ObjectMapper mapper;

  public WindowStateSerde() {
    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new JavaTimeModule());
  }

  @Override
  public Serializer<WindowState> serializer() {
    return new WindowStateSerializer(mapper);
  }

  @Override
  public Deserializer<WindowState> deserializer() {
    return new WindowStateDeserializer(mapper);
  }

  private static class WindowStateSerializer implements Serializer<WindowState> {
    private final ObjectMapper mapper;

    public WindowStateSerializer(ObjectMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public byte[] serialize(String topic, WindowState data) {
      try {
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("discountGenerated", data.isDiscountGenerated());
        rootNode.set("events", mapper.valueToTree(data.getEvents()));
        return mapper.writeValueAsBytes(rootNode);
      } catch (Exception e) {
        throw new RuntimeException("Error serializing WindowState", e);
      }
    }
  }

  private static class WindowStateDeserializer implements Deserializer<WindowState> {
    private final ObjectMapper mapper;

    public WindowStateDeserializer(ObjectMapper mapper) {
      this.mapper = mapper;
    }

    @Override
    public WindowState deserialize(String topic, byte[] data) {
      try {
        WindowState state = new WindowState();
        JsonNode rootNode = mapper.readTree(data);
        JsonNode eventsNode = rootNode.path("events");

        for (JsonNode eventNode : eventsNode) {
          state.addEvent(EventDeserializationHelper.deserializeEvent(mapper, eventNode));
        }

        state.setDiscountGenerated(rootNode.path("discountGenerated").asBoolean());
        return state;
      } catch (Exception e) {
        throw new RuntimeException("Error deserializing WindowState", e);
      }
    }
  }
}
