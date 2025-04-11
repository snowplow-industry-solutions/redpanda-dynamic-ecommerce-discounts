package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigurationManager implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String PROPERTIES_FILE = "application.properties";

  private final Properties properties;

  private final Duration continuousViewWindowDuration;
  private final Duration mostViewedWindowDuration;

  private final int continuousViewMinPings;
  private final int mostViewedMinViews;

  private final double continuousViewDiscountRate;
  private final double mostViewedDiscountRate;

  private final Duration discountCooldownPeriod;

  private final int kafkaTransactionTimeoutMs;

  private ConfigurationManager() {
    this.properties = loadProperties();

    this.continuousViewWindowDuration = Duration.ofSeconds(
        Long.parseLong(properties.getProperty("window.continuous-view.duration.seconds", "90")));
    this.mostViewedWindowDuration = Duration.ofMinutes(
        Long.parseLong(properties.getProperty("window.most-viewed.duration.minutes", "5")));
    this.continuousViewMinPings = Integer.parseInt(
        properties.getProperty("discount.continuous-view.min-pings", "9"));
    this.mostViewedMinViews = Integer.parseInt(
        properties.getProperty("discount.most-viewed.min-views", "3"));
    this.continuousViewDiscountRate = Double.parseDouble(
        properties.getProperty("discount.continuous-view.rate", "0.1"));
    this.mostViewedDiscountRate = Double.parseDouble(
        properties.getProperty("discount.most-viewed.rate", "0.1"));
    this.discountCooldownPeriod = Duration.ofMinutes(
        Long.parseLong(properties.getProperty("discount.cooldown.minutes", "5")));
    this.kafkaTransactionTimeoutMs = Integer.parseInt(
        properties.getProperty("kafka.transaction.timeout.ms", "30000"));

    logConfiguration();
  }

  private static Properties loadProperties() {
    final Properties properties = new Properties();
    try (InputStream input = ConfigurationManager.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
      if (input == null) {
        log.error("Unable to find {}", PROPERTIES_FILE);
        throw new IllegalStateException(PROPERTIES_FILE + " file not found");
      }
      properties.load(input);
      log.debug("Loaded properties from {}", PROPERTIES_FILE);
    } catch (IOException ex) {
      log.error("Error loading {}", PROPERTIES_FILE, ex);
      throw new IllegalStateException("Failed to load " + PROPERTIES_FILE, ex);
    }
    return properties;
  }

  private void logConfiguration() {
    log.info("Configuration loaded with values:");
    log.info("  Continuous view window: {}s", continuousViewWindowDuration.getSeconds());
    log.info("  Most viewed window: {}m", mostViewedWindowDuration.toMinutes());
    log.info("  Minimum pings for continuous view: {}", continuousViewMinPings);
    log.info("  Minimum views for most viewed: {}", mostViewedMinViews);
    log.info("  Continuous view discount rate: {}%", continuousViewDiscountRate * 100);
    log.info("  Most viewed discount rate: {}%", mostViewedDiscountRate * 100);
    log.info("  Discount cooldown period: {}m", discountCooldownPeriod.toMinutes());
    log.info("  Kafka transaction timeout: {}ms", kafkaTransactionTimeoutMs);
  }

  private static volatile ConfigurationManager instance;

  public static ConfigurationManager getInstance() {
    if (instance == null) {
      synchronized (ConfigurationManager.class) {
        if (instance == null) {
          instance = new ConfigurationManager();
        }
      }
    }
    return instance;
  }

  public Properties getProperties() {
    return new Properties(properties);
  }

  public Duration getContinuousViewWindowDuration() {
    return continuousViewWindowDuration;
  }

  public Duration getMostViewedWindowDuration() {
    return mostViewedWindowDuration;
  }

  public int getContinuousViewMinPings() {
    return continuousViewMinPings;
  }

  public int getMostViewedMinViews() {
    return mostViewedMinViews;
  }

  public double getContinuousViewDiscountRate() {
    return continuousViewDiscountRate;
  }

  public double getMostViewedDiscountRate() {
    return mostViewedDiscountRate;
  }

  public Duration getDiscountCooldownPeriod() {
    return discountCooldownPeriod;
  }

  public int getKafkaTransactionTimeoutMs() {
    return kafkaTransactionTimeoutMs;
  }

  public String getBootstrapServers() {
    return properties.getProperty("bootstrap.servers");
  }

  public String getInputTopic() {
    return properties.getProperty("input.topic");
  }

  public String getOutputTopic() {
    return properties.getProperty("output.topic");
  }

  public String getGroupId() {
    return properties.getProperty("group.id");
  }

  public int getParallelism() {
    return Integer.parseInt(properties.getProperty("parallelism", "1"));
  }

  public Long getCheckpointInterval() {
    String interval = properties.getProperty("checkpoint.interval.ms");
    return interval != null && !interval.isEmpty() ? Long.parseLong(interval) : null;
  }
}
