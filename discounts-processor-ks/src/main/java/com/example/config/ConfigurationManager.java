package com.example.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

@Slf4j
public class ConfigurationManager {
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
        try (InputStream input = ConfigurationManager.class.getClassLoader().getResourceAsStream("application.properties")) {
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
        log.info("  Processor enabled: {}", isProcessorEnabled());
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

    public boolean isProcessorEnabled() {
        return Boolean.parseBoolean(properties.getProperty("processor.continuous-view.enabled"));
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
        return Duration.ofSeconds(Integer.parseInt(properties.getProperty("window.continuous-view.duration.seconds")));
    }

    public int getPingIntervalSeconds() {
        return Integer.parseInt(properties.getProperty("window.continuous-view.ping-interval.seconds"));
    }

    public int getMinPings() {
        return Integer.parseInt(properties.getProperty("discount.continuous-view.min-pings"));
    }

    public double getDiscountRate() {
        return Double.parseDouble(properties.getProperty("discount.continuous-view.rate"));
    }

    public Duration getCalculatedMinDuration() {
        return Duration.ofSeconds((long) getPingIntervalSeconds() * getMinPings());
    }
}
