package com.example.serialization;

import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

public class PagePingEventListSerde implements Serde<ArrayList<PagePingEvent>> {
    private static final Logger log = LoggerFactory.getLogger(PagePingEventListSerde.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public void close() {
    }

    @Override
    public Serializer<ArrayList<PagePingEvent>> serializer() {
        return new PagePingEventListSerializer();
    }

    @Override
    public Deserializer<ArrayList<PagePingEvent>> deserializer() {
        return new PagePingEventListDeserializer();
    }

    private static class PagePingEventListSerializer implements Serializer<ArrayList<PagePingEvent>> {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
        }

        @Override
        public byte[] serialize(String topic, ArrayList<PagePingEvent> data) {
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                log.error("Failed to serialize PagePingEvent list", e);
                throw new RuntimeException("Failed to serialize PagePingEvent list", e);
            }
        }

        @Override
        public void close() {
        }
    }

    private static class PagePingEventListDeserializer implements Deserializer<ArrayList<PagePingEvent>> {
        @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
        }

        @Override
        public ArrayList<PagePingEvent> deserialize(String topic, byte[] data) {
            if (data == null) {
                return null;
            }

            try {
                JsonNode arrayNode = objectMapper.readTree(data);
                ArrayList<PagePingEvent> result = new ArrayList<>();
                
                for (JsonNode node : arrayNode) {
                    String eventName = node.path("event_name").asText();
                    if ("product_view".equals(eventName)) {
                        result.add(objectMapper.treeToValue(node, ProductViewEvent.class));
                    } else {
                        result.add(objectMapper.treeToValue(node, PagePingEvent.class));
                    }
                }
                
                return result;
            } catch (Exception e) {
                log.error("Failed to deserialize PagePingEvent list", e);
                throw new RuntimeException("Failed to deserialize PagePingEvent list", e);
            }
        }

        @Override
        public void close() {
        }
    }
}