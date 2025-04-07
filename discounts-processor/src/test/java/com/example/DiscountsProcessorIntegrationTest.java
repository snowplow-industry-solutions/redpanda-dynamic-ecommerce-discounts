package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.model.DiscountEvent;
import com.example.model.Event;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@Testcontainers
public class DiscountsProcessorIntegrationTest {

    private static final String INPUT_TOPIC = "test-snowplow-enriched-good";
    private static final String OUTPUT_TOPIC = "test-shopper-discounts";
    private static final int NUM_PARTITIONS = 1;
    private static final short REPLICATION_FACTOR = 1;
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> consumer;
    private static AdminClient adminClient;
    private static ExecutorService executorService;
    private static Future<?> processorFuture;
    private static final AtomicBoolean keepRunning = new AtomicBoolean(true);


    @Container
    public static final RedpandaContainer redpanda = new RedpandaContainer(
            "docker.redpanda.com/redpandadata/redpanda:v24.3.6"
    );

    @BeforeAll
    public static void setup() throws Exception {
        createLogDirectory();

        System.out.println("\n===================================================");
        System.out.println("STARTING REDPANDA INTEGRATION TESTS");
        System.out.println("==================================================\n");

        log.info("Starting Redpanda integration test setup...");
        
        Properties adminProps = new Properties();
        String bootstrapServers = redpanda.getBootstrapServers();
        log.info("Redpanda bootstrap servers: {}", bootstrapServers);
        System.out.println("\n[CONNECTION] Redpanda bootstrap servers: " + bootstrapServers);
        System.out.println("[CONNECTION] Container ID: " + redpanda.getContainerId());
        System.out.println("[CONNECTION] Schema Registry Address: " + redpanda.getSchemaRegistryAddress());
        adminProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        adminClient = AdminClient.create(adminProps);

        List<NewTopic> topics = new ArrayList<>();
        topics.add(new NewTopic(INPUT_TOPIC, NUM_PARTITIONS, REPLICATION_FACTOR));
        topics.add(new NewTopic(OUTPUT_TOPIC, NUM_PARTITIONS, REPLICATION_FACTOR));
        log.info("Creating topics: {} and {}", INPUT_TOPIC, OUTPUT_TOPIC);
        adminClient.createTopics(topics).all().get();
        log.info("Topics created successfully");

        Thread.sleep(2000);

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "30000");
        producerProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "30000");
        producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, "3");
        log.info("Creating Kafka producer");
        producer = new KafkaProducer<>(producerProps);
        log.info("Kafka producer created successfully");

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000");
        consumerProps.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "30000");
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        log.info("Creating Kafka consumer");
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(OUTPUT_TOPIC));
        log.info("Kafka consumer subscribed to topic: {}", OUTPUT_TOPIC);

        log.info("Starting Flink processor in a separate thread");
        executorService = Executors.newSingleThreadExecutor();
        processorFuture = executorService.submit(() -> {
            try {
                log.debug("Configuring Flink job");
                
                StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
                env.setParallelism(1);
                env.enableCheckpointing(1000);
                env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 1000));

                Properties properties = new Properties();
                properties.setProperty("bootstrap.servers", bootstrapServers);
                properties.setProperty("group.id", "test-discounts-processor");
                properties.setProperty("auto.offset.reset", "earliest");

                FlinkKafkaConsumer<Event> consumer = new FlinkKafkaConsumer<>(
                    INPUT_TOPIC,
                    new EventDeserializationSchema(),
                    properties
                );

                WatermarkStrategy<Event> watermarkStrategy = WatermarkStrategy
                    .<Event>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                    .withTimestampAssigner((event, timestamp) -> event.getCollectorTimestamp().toEpochMilli());

                DataStream<Event> events = env
                    .addSource(consumer)
                    .assignTimestampsAndWatermarks(watermarkStrategy);

                DataStream<Event> pagePings = events
                    .filter(event -> "page_ping".equals(event.getEventName()));

                DataStream<Event> productViews = events
                    .filter(event -> "snowplow_ecommerce_action".equals(event.getEventName()));

                DataStream<DiscountEvent> continuousViewDiscounts = pagePings
                    .keyBy(event -> event.getUserId() + "_" + event.getWebpageId())
                    .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                    .process(new DiscountsProcessor.ContinuousViewProcessor());

                DataStream<DiscountEvent> mostViewedDiscounts = productViews
                    .keyBy(Event::getProductId)
                    .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                    .process(new DiscountsProcessor.MostViewedProcessor());

                DataStream<DiscountEvent> allDiscounts = continuousViewDiscounts
                    .union(mostViewedDiscounts);

                allDiscounts.addSink(
                    new FlinkKafkaProducer<>(
                        OUTPUT_TOPIC,
                        new DiscountEventSerializationSchema(),
                        properties
                    )
                );

                log.info("Executing Flink job: Test Discounts Processor");
                env.executeAsync("Test Discounts Processor");
                log.info("Flink job started successfully");

                while (keepRunning.get()) {
                    Thread.sleep(100);
                }
                log.info("Flink job thread terminating");
            } catch (Exception e) {
                log.error("Error in Flink processor thread", e);
            }
        });

        log.info("Waiting for processor to start...");
        Thread.sleep(5000);
        log.info("Setup complete, ready to run tests");
    }

    private static void createLogDirectory() {
        try {
            Path logDir = Paths.get("build", "test-logs");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("integration-test.log");
            log.info("Created log directory: {}", logDir.toAbsolutePath());
            log.info("Log file will be written to: {}", logFile.toAbsolutePath());
            System.out.println("\n===================================================");
            System.out.println("Integration test logs will be written to: " + logFile.toAbsolutePath());
            System.out.println("You can view these logs even if the test is aborted");
            System.out.println("==================================================\n");
        } catch (Exception e) {
            log.warn("Failed to create log directory", e);
        }
    }

    private void debugFlinkJob() {
        try {
            System.out.println("\n[DEBUG] Checking events in input topic...");
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers());
            AdminClient adminClient = AdminClient.create(props);
            Map<TopicPartition, OffsetSpec> offsetSpecs = new HashMap<>();
            offsetSpecs.put(new TopicPartition(INPUT_TOPIC, 0), OffsetSpec.earliest());
            offsetSpecs.put(new TopicPartition(INPUT_TOPIC, 0), OffsetSpec.latest());
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> inputOffsets = adminClient.listOffsets(offsetSpecs).all().get();
            System.out.println("[DEBUG] Input topic offsets: " + inputOffsets);

            System.out.println("\n[DEBUG] Checking events in output topic...");
            offsetSpecs.clear();
            offsetSpecs.put(new TopicPartition(OUTPUT_TOPIC, 0), OffsetSpec.earliest());
            offsetSpecs.put(new TopicPartition(OUTPUT_TOPIC, 0), OffsetSpec.latest());
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> outputOffsets = adminClient.listOffsets(offsetSpecs).all().get();
            System.out.println("[DEBUG] Output topic offsets: " + outputOffsets);

            adminClient.close();
        } catch (Exception e) {
            System.out.println("[DEBUG] Error debugging Flink job: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testNoDiscountWhenThresholdNotReached() throws Exception {
        log.info("Starting No Discount When Threshold Not Reached test");
        System.out.println("\n===================================================");
        System.out.println("STARTING TEST: No Discount When Threshold Not Reached");
        System.out.println("==================================================\n");

        String userId = "test-user-" + UUID.randomUUID().toString();
        String productId = "test-product-" + UUID.randomUUID().toString();
        String webpageId = "test-page-" + UUID.randomUUID().toString();

        log.info("Running No Discount test with:");
        log.info("  User ID: {}", userId);
        log.info("  Product ID: {}", productId);
        log.info("  Webpage ID: {}", webpageId);

        log.info("Sending a single event, which is below the threshold");
        System.out.println("\n[TEST] Sending a single event, which is below the threshold");

        Event event = new Event(
            productId,
            userId,
            "page_ping",
            webpageId,
            java.time.Instant.now()
        );

        String eventJson = objectMapper.writeValueAsString(event);
        log.info("Sending event: {}", eventJson);
        System.out.println("[TEST] Sending event: " + eventJson);

        producer.send(new ProducerRecord<>(INPUT_TOPIC, userId, eventJson)).get();

        log.info("Waiting to verify no discount is generated");
        System.out.println("\n[TEST] Waiting to verify no discount is generated (15 seconds)...");

        Thread.sleep(15000);

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));
        log.info("Polled {} records from output topic", records.count());
        System.out.println("[TEST] Polled " + records.count() + " records from output topic");

        boolean foundDiscount = false;
        for (ConsumerRecord<String, String> record : records) {
            log.info("Received record: {}", record.value());
            System.out.println("[TEST] Received record: " + record.value());

            DiscountEvent receivedEvent = objectMapper.readValue(record.value(), DiscountEvent.class);

            if (userId.equals(receivedEvent.getUserId()) &&
                productId.equals(receivedEvent.getProductId())) {
                foundDiscount = true;
                break;
            }
        }

        assertFalse(foundDiscount, "No discount should be generated when below threshold");

        log.info("No Discount When Threshold Not Reached test completed successfully");
        System.out.println("\n[TEST] No Discount When Threshold Not Reached test completed successfully");
    }

    @AfterAll
    public static void teardown() {
        System.out.println("\n===================================================");
        System.out.println("FINISHING REDPANDA INTEGRATION TESTS");
        System.out.println("==================================================\n");

        log.info("Starting test teardown...");

        log.info("Stopping Flink processor");
        keepRunning.set(false);
        if (processorFuture != null) {
            processorFuture.cancel(true);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }

        log.info("Closing Kafka resources");
        if (producer != null) {
            producer.close();
        }
        if (consumer != null) {
            consumer.close();
        }
        if (adminClient != null) {
            adminClient.close();
        }
        log.info("Teardown complete");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testContinuousViewDiscount() throws Exception {
        log.info("Starting Continuous View Discount test");
        System.out.println("\n===================================================");
        System.out.println("STARTING TEST: Continuous View Discount");
        System.out.println("==================================================\n");

        String userId = "test-user-" + UUID.randomUUID().toString();
        String productId = "test-product-" + UUID.randomUUID().toString();
        String webpageId = "test-page-" + UUID.randomUUID().toString();

        log.info("Running Continuous View Discount test with:");
        log.info("  User ID: {}", userId);
        log.info("  Product ID: {}", productId);
        log.info("  Webpage ID: {}", webpageId);

        log.info("Sending multiple page_ping events to trigger continuous view discount");
        System.out.println("\n[TEST] Sending multiple page_ping events to trigger continuous view discount");

        long baseTime = System.currentTimeMillis();
        int numEvents = 5;

        for (int i = 0; i < numEvents; i++) {
            Event event = new Event(
                productId,
                userId,
                "page_ping",
                webpageId,
                java.time.Instant.ofEpochMilli(baseTime + (i * 1000))
            );

            String eventJson = objectMapper.writeValueAsString(event);
            log.info("Sending event {}/{}: {}", (i+1), numEvents, eventJson);
            System.out.println("[TEST] Sending event " + (i+1) + "/" + numEvents + ": " + eventJson);

            producer.send(new ProducerRecord<>(INPUT_TOPIC, userId, eventJson)).get();

            Thread.sleep(100);
        }

        log.info("Waiting for events to be processed and discounts to be generated");
        System.out.println("\n[TEST] Waiting for continuous view discount to be generated (up to 120 seconds)...");

        debugFlinkJob();

        await().atMost(120, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).pollDelay(10, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            log.debug("Polled {} records from output topic", records.count());
            boolean foundDiscount = false;

            for (ConsumerRecord<String, String> record : records) {
                log.info("Received record: {}", record.value());
                System.out.println("[TEST] Received record: " + record.value());

                DiscountEvent receivedEvent = objectMapper.readValue(record.value(), DiscountEvent.class);

                if (userId.equals(receivedEvent.getUserId()) &&
                    productId.equals(receivedEvent.getProductId())) {

                    log.info("Found matching discount for user {} and product {}", userId, productId);
                    System.out.println("\n[TEST] Found continuous view discount for user " + userId + " and product " + productId);
                    foundDiscount = true;

                    assertNotNull(receivedEvent.getDiscount(), "Discount should not be null");
                    assertEquals(0.1, receivedEvent.getDiscount().getRate(), 0.001, "Discount rate should be 10%");
                }
            }

            assertTrue(foundDiscount, "Continuous view discount was not found");
        });

        log.info("Continuous View Discount test completed successfully");
        System.out.println("\n[TEST] Continuous View Discount test completed successfully");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void testMostViewedProductDiscount() throws Exception {
        log.info("Starting Most Viewed Product Discount test");
        System.out.println("\n===================================================");
        System.out.println("STARTING TEST: Most Viewed Product Discount");
        System.out.println("==================================================\n");

        String userId = "test-user-" + UUID.randomUUID().toString();
        String productId = "test-product-" + UUID.randomUUID().toString();

        log.info("Running Most Viewed Product test with:");
        log.info("  User ID: {}", userId);
        log.info("  Product ID: {}", productId);

        log.info("Sending multiple product view events to trigger most viewed product discount");
        System.out.println("\n[TEST] Sending multiple product view events to trigger most viewed product discount");

        long baseTime = System.currentTimeMillis();
        int numEvents = 3;

        for (int i = 0; i < numEvents; i++) {
            Event event = new Event(
                productId,
                userId,
                "snowplow_ecommerce_action",
                "product-page-" + i,
                java.time.Instant.ofEpochMilli(baseTime + (i * 1000))
            );

            String eventJson = objectMapper.writeValueAsString(event);
            log.info("Sending event {}/{}: {}", (i+1), numEvents, eventJson);
            System.out.println("[TEST] Sending event " + (i+1) + "/" + numEvents + ": " + eventJson);

            producer.send(new ProducerRecord<>(INPUT_TOPIC, productId, eventJson)).get();

            Thread.sleep(100);
        }

        log.info("Waiting for events to be processed and discounts to be generated");
        System.out.println("\n[TEST] Waiting for most viewed product discount to be generated (up to 120 seconds)...");

        await().atMost(120, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            log.debug("Polled {} records from output topic", records.count());
            boolean foundDiscount = false;

            for (ConsumerRecord<String, String> record : records) {
                log.info("Received record: {}", record.value());
                System.out.println("[TEST] Received record: " + record.value());

                DiscountEvent receivedEvent = objectMapper.readValue(record.value(), DiscountEvent.class);

                if (productId.equals(receivedEvent.getProductId())) {
                    log.info("Found matching discount for product {}", productId);
                    System.out.println("\n[TEST] Found most viewed product discount for product " + productId);
                    foundDiscount = true;

                    assertNotNull(receivedEvent.getDiscount(), "Discount should not be null");
                    assertEquals(0.10, receivedEvent.getDiscount().getRate(), 0.001, "Discount rate should be 10%");
                }
            }

            assertTrue(foundDiscount, "Most viewed product discount was not found");
        });

        log.info("Most Viewed Product Discount test completed successfully");
        System.out.println("\n[TEST] Most Viewed Product Discount test completed successfully");
    }
}
