package com.example.config;

import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigurationManager {

  public static final String MOST_VIEWED_LAST_DISCOUNT_STORE = "last-discount-store";
  public static final String MOST_VIEWED_VIEWS_STORE = "views-store";
  public static final String MOST_VIEWED_DURATION_STORE = "duration-store";

  public static final String CONTINUOUS_VIEW_LAST_DISCOUNT_STORE = "continuous-view-last-discount";
  public static final String CONTINUOUS_VIEW_EVENTS_STORE = "continuous-view-events";
  public static final String CONTINUOUS_VIEW_START_TIME_STORE = "continuous-view-start-time";

  private static ConfigurationManager instance;
  private final Properties properties;

  private ConfigurationManager() {
    this(loadDefaultProperties());
    validateConfiguration();
  }

  private ConfigurationManager(Properties properties) {
    this.properties = properties;
    validateConfiguration();
  }

  @SneakyThrows
  private static Properties getResourceAsStream(Properties props, String path) {
    InputStream input = ConfigurationManager.class.getClassLoader().getResourceAsStream(path);
    props.load(input);
    return props;
  }

  private static Properties loadDefaultProperties() {
    Properties properties = new Properties();
    log.info("Loading configuration from application.properties...");
    return getResourceAsStream(properties, "application.properties");
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

  public void logConfiguration() {
    log.info("Configuration loaded:");
    log.info("  Enabled processors: {}", getEnabledProcessors());
    if (isContinuousViewProcessorEnabled()) {
      log.info("  Continuous view implementation: {}", getContinuousViewImplementation());
    }
    log.info("  Bootstrap servers: {}", getBootstrapServers());
    log.info("  Input topic: {}", getInputTopic());
    log.info("  Output topic: {}", getOutputTopic());
    log.info("  Group ID: {}", getGroupId());
    log.info("  Auto offset reset: {}", getAutoOffsetReset());
    log.info("  Window duration: {}s", getWindowDuration().getSeconds());
    log.info("  Ping interval: {}s", getPingIntervalSeconds());
    log.info("  Minimum pings: {}", getMinPingsForContinuousViewDiscount());
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
        Integer.parseInt(properties.getProperty("processor.window.duration.seconds", "300")));
  }

  public long getWindowDurationSeconds() {
    return getWindowDuration().getSeconds();
  }

  public Long getWindowDurationMs() {
    return getWindowDuration().toMillis();
  }

  public long getPingIntervalSeconds() {
    return Integer.parseInt(
        properties.getProperty("processor.continuous-view.ping-interval.seconds", "10"));
  }

  public int getMinPingsForContinuousViewDiscount() {
    return Integer.parseInt(
        properties.getProperty("processor.continuous-view.min-pings-for-discount", "9"));
  }

  public double getDiscountRate() {
    return Double.parseDouble(properties.getProperty("processor.discount-rate", "0.1"));
  }

  public boolean isContinuousViewProcessorEnabled() {
    return Boolean.parseBoolean(
        properties.getProperty("processor.continuous-view.enabled", "true"));
  }

  public boolean isMostViewedProcessorEnabled() {
    return Boolean.parseBoolean(properties.getProperty("processor.most-viewed.enabled", "false"));
  }

  public boolean hasAnyProcessorEnabled() {
    return isContinuousViewProcessorEnabled() || isMostViewedProcessorEnabled();
  }

  public int getMinViewsForMostViewedDiscount() {
    return Integer.parseInt(
        properties.getProperty("processor.most-viewed.min-views-for-discount", "5"));
  }

  public int getContinuousViewImplementation() {
    return Integer.parseInt(
        properties.getProperty("processor.continuous-view.implementation", "1"));
  }

  public long getDelayToFirstPingSeconds() {
    return Integer.parseInt(
        properties.getProperty("processor.continuous-view.delay-to-first-ping.seconds", "10"));
  }

  public long calculateTotalViewingSeconds(long numberOfPings) {
    long delayToFirstPingSeconds = getDelayToFirstPingSeconds();
    long pingIntervalSeconds = getPingIntervalSeconds();
    return delayToFirstPingSeconds + (numberOfPings * pingIntervalSeconds);
  }

  private void validateConfiguration() {
    int continuousViewImplementation = getContinuousViewImplementation();

    if (continuousViewImplementation != 1 && continuousViewImplementation != 2) {
      throw new IllegalArgumentException(
          "processor.continuous-view.implementation must be either 1 or 2");
    }
  }
}
