package com.example;

import com.example.model.Event;
import com.example.model.DiscountEvent;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscountsProcessor {
    public static void main(String[] args) throws Exception {
        log.info("Starting Discounts Processor application");

        Properties config = loadConfig();
        log.info("Loaded configuration: {}", config);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        log.info("Created Flink StreamExecutionEnvironment");

        int parallelism = Integer.parseInt(config.getProperty("parallelism", "1"));
        env.setParallelism(parallelism);
        log.info("Set parallelism to: {}", parallelism);

        String checkpointInterval = config.getProperty("checkpoint.interval.ms");
        if (checkpointInterval != null && !checkpointInterval.isEmpty()) {
            env.enableCheckpointing(Long.parseLong(checkpointInterval));
            log.info("Enabled checkpointing with interval: {}ms", checkpointInterval);
        }

        String bootstrapServers = determineBootstrapServers(config);
        log.info("Using bootstrap servers: {}", bootstrapServers);

        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", bootstrapServers);
        kafkaProps.setProperty("group.id", config.getProperty("group.id", "discounts-processor"));
        kafkaProps.setProperty("auto.offset.reset", config.getProperty("auto.offset.reset", "earliest"));

        Properties consumerProps = new Properties();
        consumerProps.putAll(kafkaProps);
        consumerProps.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        Properties producerProps = new Properties();
        producerProps.putAll(kafkaProps);
        producerProps.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.setProperty("transaction.timeout.ms", "30000");

        String inputTopic = config.getProperty("input.topic", "snowplow-enriched-good");
        log.info("Using input topic: {}", inputTopic);

        FlinkKafkaConsumer<Event> consumer = new FlinkKafkaConsumer<>(
            inputTopic,
            new EventDeserializationSchema(),
            consumerProps
        );
        log.info("Created Kafka consumer for topic: {}", inputTopic);

        WatermarkStrategy<Event> watermarkStrategy = WatermarkStrategy
            .<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner((event, timestamp) -> {
                long eventTime = event.getCollectorTimestamp().toEpochMilli();
                log.info("Assigning timestamp {} to event: {}", eventTime, event.getEventName());
                return eventTime;
            })
            .withIdleness(Duration.ofSeconds(1));
        log.info("Configured watermark strategy with 5 seconds bounded out-of-orderness and 1 second idleness");

        DataStream<Event> events = env
            .addSource(consumer)
            .assignTimestampsAndWatermarks(watermarkStrategy);
        log.info("Created input DataStream with timestamps and watermarks");

        events = events.map(event -> {
            log.info("Received event: event_name={}, product_id={}, user_id={}, webpage_id={}",
                    event.getEventName(), event.getProductId(), event.getUserId(), event.getWebpageId());
            return event;
        });

        DataStream<Event> pagePings = events
            .filter(event -> {
                boolean isPagePing = "page_ping".equals(event.getEventName());
                if (isPagePing) {
                    log.info("Filtered page_ping event: product_id={}, user_id={}, webpage_id={}",
                            event.getProductId(), event.getUserId(), event.getWebpageId());
                }
                return isPagePing;
            });
        log.info("Created page_ping events stream");

        DataStream<Event> productViews = events
            .filter(event -> {
                boolean isProductView = "snowplow_ecommerce_action".equals(event.getEventName());
                if (isProductView) {
                    log.info("Filtered product_view event: product_id={}, user_id={}, webpage_id={}",
                            event.getProductId(), event.getUserId(), event.getWebpageId());
                }
                return isProductView;
            });
        log.info("Created product_view events stream");

        pagePings = pagePings.map(event -> {
            log.info("Page ping before windowing: user_id={}, webpage_id={}, timestamp={}",
                    event.getUserId(), event.getWebpageId(), event.getCollectorTimestamp());
            return event;
        });

        final var CONTINUOUS_VIEW_WINDOW_SIZE = Time.seconds(10); // Reduced from 90 to 10 for easier testing

        DataStream<DiscountEvent> continuousViewDiscounts = pagePings
            .keyBy(event -> {
                String key = event.getUserId() + "_" + event.getWebpageId();
                log.info("Keying page ping by: {}", key);
                return key;
            })
            .window(TumblingEventTimeWindows.of(CONTINUOUS_VIEW_WINDOW_SIZE))
            .process(new ContinuousViewProcessor());
        log.info("Set up continuous view discount processing with 10 second window");

        productViews = productViews.map(event -> {
            log.info("Product view before windowing: product_id={}, timestamp={}",
                    event.getProductId(), event.getCollectorTimestamp());
            return event;
        });

        final var MOST_VIEWED_WINDOW_SIZE = Time.seconds(30); // Reduced from 5 minutes to 30 seconds for easier testing

        DataStream<DiscountEvent> mostViewedDiscounts = productViews
            .keyBy(event -> {
                String key = event.getProductId();
                log.info("Keying product view by: {}", key);
                return key;
            })
            .window(TumblingEventTimeWindows.of(MOST_VIEWED_WINDOW_SIZE))
            .process(new MostViewedProcessor());
        log.info("Set up most viewed product discount processing with 30 second window");

        DataStream<DiscountEvent> allDiscounts = continuousViewDiscounts
            .union(mostViewedDiscounts);
        log.debug("Combined discount streams");

        String outputTopic = config.getProperty("output.topic", "shopper-discounts");
        log.info("Using output topic: {}", outputTopic);

        allDiscounts.addSink(
            new FlinkKafkaProducer<>(
                outputTopic,
                new DiscountEventSerializationSchema(),
                producerProps
            )
        );
        log.info("Added Kafka sink for discount events");

        log.info("Starting Flink job execution");
        env.execute("Discounts Processor");
    }

    private static String determineBootstrapServers(Properties config) {
        String containerName = System.getenv("CONTAINER_NAME");
        String servers;
        if (containerName != null && !containerName.isEmpty()) {
            servers = config.getProperty("bootstrap.servers.docker", "localhost:9092");
            log.info("Running in Docker container: {}, using docker bootstrap servers", containerName);
        } else {
            servers = config.getProperty("bootstrap.servers.host", "localhost:19092");
            log.info("Running on host machine, using host bootstrap servers");
        }
        return servers;
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = DiscountsProcessor.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                log.warn("Unable to find application.properties, using defaults");
                return properties;
            }
            properties.load(input);
            log.debug("Loaded properties from application.properties");
        } catch (IOException ex) {
            log.error("Error loading application.properties", ex);
        }
        return properties;
    }

    public static class ContinuousViewProcessor extends ProcessWindowFunction<Event, DiscountEvent, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Event> elements, Collector<DiscountEvent> out) {
            long count = 0;
            Event lastEvent = null;

            for (Event event : elements) {
                count++;
                lastEvent = event;
            }

            if (count >= 3 && lastEvent != null) { // Reduced threshold for testing
                DiscountEvent discount = new DiscountEvent();
                discount.setUserId(lastEvent.getUserId());
                discount.setProductId(lastEvent.getProductId());

                DiscountEvent.Discount discountDetails = new DiscountEvent.Discount();
                discountDetails.setRate(0.1);

                DiscountEvent.ViewTime viewTime = new DiscountEvent.ViewTime();
                viewTime.setDurationInSeconds((int) (count * 10));
                discountDetails.setByViewTime(viewTime);

                discount.setDiscount(discountDetails);
                out.collect(discount);
            }
        }
    }

    public static class MostViewedProcessor extends ProcessWindowFunction<Event, DiscountEvent, String, TimeWindow> {
        final byte VIEW_THRESHOLD = 2;

        @Override
        public void process(String productId, Context context, Iterable<Event> elements, Collector<DiscountEvent> out) {
            Map<String, Integer> userViews = new HashMap<>();
            Map<String, Integer> userDurations = new HashMap<>();
            String mostViewedUserId = null;
            int maxViews = 0;
            int maxDuration = 0;

            for (Event event : elements) {
                String userId = event.getUserId();
                userViews.merge(userId, 1, Integer::sum);
                userDurations.merge(userId, 10, Integer::sum);

                int views = userViews.get(userId);
                int duration = userDurations.get(userId);

                if (views > maxViews || (views == maxViews && duration > maxDuration)) {
                    maxViews = views;
                    maxDuration = duration;
                    mostViewedUserId = userId;
                }
            }

            if (mostViewedUserId != null && maxViews >= VIEW_THRESHOLD) {
                DiscountEvent discount = new DiscountEvent();
                discount.setUserId(mostViewedUserId);
                discount.setProductId(productId);

                DiscountEvent.Discount discountDetails = new DiscountEvent.Discount();
                discountDetails.setRate(0.1);

                DiscountEvent.ViewTime viewTime = new DiscountEvent.ViewTime();
                viewTime.setDurationInSeconds(maxDuration);
                discountDetails.setByViewTime(viewTime);

                discount.setDiscount(discountDetails);
                out.collect(discount);
            }
        }
    }
}