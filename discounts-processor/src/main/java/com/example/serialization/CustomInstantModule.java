package com.example.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class CustomInstantModule extends SimpleModule {
  public CustomInstantModule() {
    addDeserializer(
        Instant.class,
        new JsonDeserializer<Instant>() {
          @Override
          public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getText();
            try {
              return Instant.parse(value);
            } catch (DateTimeParseException e) {
              if (value != null && value.contains(".")) {
                int dot = value.indexOf('.');
                int z = value.indexOf('Z', dot);
                if (z > dot) {
                  String millis = value.substring(dot + 1, z);
                  if (millis.length() < 3) {
                    StringBuilder sb = new StringBuilder(value);
                    for (int i = 0; i < 3 - millis.length(); i++) {
                      sb.insert(z, "0");
                    }
                    value = sb.toString();
                  }
                }
              }
              return Instant.parse(value);
            }
          }
        });
  }
}
