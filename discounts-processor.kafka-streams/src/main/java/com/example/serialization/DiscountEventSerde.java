package com.example.serialization;

import com.example.model.DiscountEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DiscountEventSerde implements Serde<DiscountEvent> {
    private static final Logger log = LoggerFactory.getLogger(DiscountEventSerde.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public void close() {
    }

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
        public void configure(Map<String, ?> configs, boolean isKey) {
        }

        @Override
        public byte[] serialize(String topic, DiscountEvent data) {
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize DiscountEvent", e);
            }
        }

        @Override
        public void close() {
        }
    }

    private static class DiscountEventDeserializer implements Deserializer<DiscountEvent> {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
        }

        @Override
        public DiscountEvent deserialize(String topic, byte[] data) {
            if (data == null) {
                return null;
            }

            try {
                return objectMapper.readValue(data, DiscountEvent.class);
            } catch (Exception e) {
                log.error("Failed to deserialize DiscountEvent", e);
                throw new RuntimeException("Failed to deserialize DiscountEvent", e);
            }
        }

        @Override
        public void close() {
        }
    }
}