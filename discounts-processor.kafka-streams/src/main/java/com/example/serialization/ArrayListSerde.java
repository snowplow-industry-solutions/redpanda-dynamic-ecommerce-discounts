package com.example.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ArrayListSerde<T> implements Serde<ArrayList<T>> {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Class<T> typeParameterClass;

  public ArrayListSerde(Class<T> typeParameterClass) {
    this.typeParameterClass = typeParameterClass;
  }

  @Override
  public Serializer<ArrayList<T>> serializer() {
    return (topic, data) -> {
      try {
        return mapper.writeValueAsBytes(data);
      } catch (Exception e) {
        throw new RuntimeException("Error serializing ArrayList", e);
      }
    };
  }

  @Override
  public Deserializer<ArrayList<T>> deserializer() {
    return (topic, data) -> {
      try {
        return mapper.readValue(
            data,
            mapper.getTypeFactory().constructCollectionType(ArrayList.class, typeParameterClass));
      } catch (Exception e) {
        throw new RuntimeException("Error deserializing ArrayList", e);
      }
    };
  }
}
