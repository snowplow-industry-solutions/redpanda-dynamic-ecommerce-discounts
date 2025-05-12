package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

@Slf4j
public class DiscountEventSender implements Runnable {
  private final KafkaConsumer<String, String> consumer;
  private final ObjectMapper objectMapper;
  private final DiscountEventSenderHelper helper;
  private volatile boolean running = true;

  public DiscountEventSender() {
    Properties props = new Properties();
    props.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        ConfigurationManager.getInstance().getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "snowplow-consumer");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    this.consumer = new KafkaConsumer<>(props);
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.helper = new DiscountEventSenderHelper();
  }

  @Override
  public void run() {
    try {
      consumer.subscribe(
          java.util.Collections.singletonList(ConfigurationManager.getInstance().getOutputTopic()));

      while (running) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

        for (ConsumerRecord<String, String> record : records) {
          try {
            DiscountEvent discountEvent =
                objectMapper.readValue(record.value(), DiscountEvent.class);
            helper.sendToSnowplow(discountEvent);
          } catch (Exception e) {
            log.error("Error processing record: {}", record.value(), e);
          }
        }
      }
    } finally {
      consumer.close();
    }
  }

  public void stop() {
    running = false;
  }
}
