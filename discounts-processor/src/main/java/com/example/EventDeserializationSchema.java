package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.model.Event;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventDeserializationSchema implements DeserializationSchema<Event> {
    private static final long serialVersionUID = 1L;
    
    private transient ObjectMapper objectMapper;
    
    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        }
        return objectMapper;
    }

    private String calculateHash(String input) {
        CRC32 crc32 = new CRC32();
        crc32.update(input.getBytes(StandardCharsets.UTF_8));
        return String.format("%08X", crc32.getValue());
    }

    @Override
    public Event deserialize(byte[] message) throws IOException {
        try {
            String jsonString = new String(message, StandardCharsets.UTF_8);
            String messageHash = calculateHash(jsonString);
            log.debug("Attempting to deserialize message [hash={}]: {}", messageHash, jsonString);
            Event event = getObjectMapper().readValue(jsonString, Event.class);
            log.debug("Successfully deserialized event [hash={}]", messageHash);
            return event;
        } catch (Exception e) {
            String jsonString = new String(message, StandardCharsets.UTF_8);
            String messageHash = calculateHash(jsonString);
            log.error("Failed to deserialize message [hash={}]", messageHash, e);
            throw e;
        }
    }

    @Override
    public boolean isEndOfStream(Event event) {
        return false;
    }

    @Override
    public TypeInformation<Event> getProducedType() {
        return TypeInformation.of(Event.class);
    }
}
