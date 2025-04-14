package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;

@Slf4j
public class ConfigurationManager implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String PROPERTIES_FILE = "application.properties";

  private final Properties properties;

  private final Duration windowDuration;
  private final Duration pingInterval;
  private final int minPings;
  private final Duration calculatedMinDuration;
  private final double discountRate;
  private final boolean processorEnabled;

  private ConfigurationManager() {
    this.properties = loadProperties();

    try {
      this.pingInterval = Duration.ofSeconds(
          Long.parseLong(properties.getProperty("window.continuous-view.ping-interval.seconds")));
      
      this.minPings = Integer.parseInt(
          properties.getProperty("discount.continuous-view.min-pings"));
      
      this.calculatedMinDuration = this.pingInterval.multipliedBy(this.minPings);

      this.windowDuration = Duration.ofSeconds(
          Long.parseLong(properties.getProperty("window.continuous-view.duration.seconds")));
      
      this.discountRate = Double.parseDouble(
          properties.getProperty("discount.continuous-view.rate"));
      
      this.processorEnabled = Boolean.parseBoolean(
          properties.getProperty("processor.continuous-view.enabled"));

      validateConfiguration();
      logConfiguration();
    } catch (Exception e) {
      log.error("Failed to initialize configuration", e);
      throw new IllegalStateException("Configuration initialization failed. Aborting application.", e);
    }
  }

  private void validateConfiguration() {
    if (windowDuration.compareTo(calculatedMinDuration) < 0) {
      throw new IllegalStateException(
          String.format("Window duration (%ds) must be greater than or equal to calculated minimum duration (%ds)",
              windowDuration.getSeconds(), calculatedMinDuration.getSeconds()));
    }
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
    log.info("  Window duration: {}s", windowDuration.getSeconds());
    log.info("  Ping interval: {}s", pingInterval.getSeconds());
    log.info("  Minimum pings required: {}", minPings);
    log.info("  Calculated minimum duration: {}s", calculatedMinDuration.getSeconds());
    log.info("  Discount rate: {}%", discountRate * 100);
    log.info("  Processor enabled: {}", processorEnabled);
    log.info("  Bootstrap servers: {}", getBootstrapServers());
    log.info("  Input topic: {}", getInputTopic());
    log.info("  Output topic: {}", getOutputTopic());
    log.info("  Group ID: {}", getGroupId());
    log.info("  Auto offset reset: {}", properties.getProperty("auto.offset.reset"));
    log.info("  Parallelism: {}", getParallelism());
    Long checkpointInterval = getCheckpointInterval();
    log.info("  Checkpoint interval: {}", checkpointInterval != null ? checkpointInterval + "ms" : "disabled");
  }

  // Getters simplificados
  public Duration getWindowDuration() { return windowDuration; }
  public Duration getPingInterval() { return pingInterval; }
  public int getMinPings() { return minPings; }
  public Duration getCalculatedMinDuration() { return calculatedMinDuration; }
  public double getDiscountRate() { return discountRate; }
  public boolean isProcessorEnabled() { return processorEnabled; }

  // Métodos existentes mantidos
  public String getBootstrapServers() { return properties.getProperty("bootstrap.servers"); }
  public String getInputTopic() { return properties.getProperty("input.topic"); }
  public String getOutputTopic() { return properties.getProperty("output.topic"); }
  public String getGroupId() { return properties.getProperty("group.id"); }
  public int getParallelism() { return Integer.parseInt(properties.getProperty("parallelism", "1")); }
  public Long getCheckpointInterval() {
    String interval = properties.getProperty("checkpoint.interval.ms");
    return interval != null && !interval.isEmpty() ? Long.parseLong(interval) : null;
  }
  public OffsetsInitializer getAutoOffsetReset() {
    String offsetReset = properties.getProperty("auto.offset.reset");
    return switch (offsetReset.toLowerCase()) {
      case "earliest" -> OffsetsInitializer.earliest();
      case "latest" -> OffsetsInitializer.latest();
      default -> throw new IllegalStateException("Invalid auto.offset.reset value: " + offsetReset);
    };
  }

  // Singleton pattern
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
}
