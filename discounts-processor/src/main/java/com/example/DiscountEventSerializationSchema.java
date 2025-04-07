package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.model.DiscountEvent;
import org.apache.flink.api.common.serialization.SerializationSchema;

public class DiscountEventSerializationSchema implements SerializationSchema<DiscountEvent> {
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Override
    public byte[] serialize(DiscountEvent discountEvent) {
        try {
            return objectMapper.writeValueAsBytes(discountEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize DiscountEvent", e);
        }
    }
}