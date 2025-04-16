package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigurationManager {
  public static final String LAST_DISCOUNT_STORE = "last-discount-store";
  public static final String VIEWS_STORE = "views-store";
  public static final String DURATION_STORE = "duration-store";
  public static final String PAGE_VIEWS_STORE = "page-views-store";

  private static ConfigurationManager instance;
  private final Properties properties;

  private ConfigurationManager() {
    this(loadDefaultProperties());
  }

  private ConfigurationManager(Properties properties) {
    this.properties = properties;
    logConfiguration();
  }

  private static Properties loadDefaultProperties() {
    Properties properties = new Properties();
    try (InputStream input =
        ConfigurationManager.class.getClassLoader().getResourceAsStream("application.properties")) {
      properties.load(input);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load configuration", e);
    }
    return properties;
  }

  public static synchronized ConfigurationManager getInstance() {
    if (instance == null) {
      instance = new ConfigurationManager();
    }
    return instance;
  }

  public static synchronized ConfigurationManager getInstance(Properties properties) {
    instance = new ConfigurationManager(properties);
    return instance;
  }

  private void logConfiguration() {
    log.info("Configuration loaded:");
    log.info("  Enabled processors: {}", getEnabledProcessors());
    log.info("  Bootstrap servers: {}", getBootstrapServers());
    log.info("  Input topic: {}", getInputTopic());
    log.info("  Output topic: {}", getOutputTopic());
    log.info("  Group ID: {}", getGroupId());
    log.info("  Auto offset reset: {}", getAutoOffsetReset());
    log.info("  Window duration: {}s", getWindowDuration().getSeconds());
    log.info("  Ping interval: {}s", getPingIntervalSeconds());
    log.info("  Minimum pings: {}", getMinPings());
    log.info("  Discount rate: {}", getDiscountRate());
  }

  private String getEnabledProcessors() {
    return (isContinuousViewProcessorEnabled() ? "ContinuousView" : "")
        + (isContinuousViewProcessorEnabled() && isMostViewedProcessorEnabled() ? ", " : "")
        + (isMostViewedProcessorEnabled() ? "MostViewed" : "");
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

  public String getAutoOffsetReset() {
    return properties.getProperty("auto.offset.reset");
  }

  public Duration getWindowDuration() {
    return Duration.ofSeconds(
        Integer.parseInt(properties.getProperty("processor.window.duration.seconds")));
  }

  public long getWindowDurationSeconds() {
    return getWindowDuration().getSeconds();
  }

  public Long getWindowDurationMs() {
    return getWindowDuration().toMillis();
  }

  public int getPingIntervalSeconds() {
    return Integer.parseInt(
        properties.getProperty("processor.continuous-view.ping-interval.seconds"));
  }

  public int getMinPings() {
    return Integer.parseInt(
        properties.getProperty("processor.continuous-view.min-pings-for-discount"));
  }

  public double getDiscountRate() {
    return Double.parseDouble(properties.getProperty("processor.discount-rate"));
  }

  public Duration getCalculatedMinDuration() {
    return Duration.ofSeconds((long) getPingIntervalSeconds() * getMinPings());
  }

  public boolean isContinuousViewProcessorEnabled() {
    return Boolean.parseBoolean(
        properties.getProperty("processor.continuous-view.enabled", "false"));
  }

  public boolean isMostViewedProcessorEnabled() {
    return Boolean.parseBoolean(properties.getProperty("processor.most-viewed.enabled", "false"));
  }

  public boolean hasAnyProcessorEnabled() {
    return isContinuousViewProcessorEnabled() || isMostViewedProcessorEnabled();
  }

  public int getMinViewsForDiscount() {
    return Integer.parseInt(properties.getProperty("processor.most-viewed.min-views-for-discount"));
  }
}
