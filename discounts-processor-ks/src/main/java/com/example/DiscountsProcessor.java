package com.example;

import com.example.config.ConfigurationManager;
import com.example.model.PagePingEvent;
import com.example.processor.ContinuousViewProcessor;
import com.example.serialization.DiscountEventSerde;
import com.example.serialization.EventSerde;
import com.example.serialization.EventTimestampExtractor;
import com.example.serialization.PagePingEventListSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Properties;

@Slf4j
public class DiscountsProcessor {
    private final ConfigurationManager config = ConfigurationManager.getInstance();
    private final Properties props;
    private final EventSerde eventSerde;
    private final DiscountEventSerde discountEventSerde;
    private final PagePingEventListSerde pagePingEventListSerde;

    public DiscountsProcessor() {
        this.props = createKafkaProperties();
        this.eventSerde = new EventSerde();
        this.discountEventSerde = new DiscountEventSerde();
        this.pagePingEventListSerde = new PagePingEventListSerde();
    }

    private Properties createKafkaProperties() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, config.getGroupId());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, EventSerde.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getAutoOffsetReset());
        return props;
    }

    public void start() {
        log.info("Initializing Discounts Processor...");

        log.info("Kafka configuration: bootstrap.servers={}, input.topic={}, output.topic={}",
            config.getBootstrapServers(), config.getInputTopic(), config.getOutputTopic());

        StreamsBuilder builder = new StreamsBuilder();

        Duration windowSize = config.getWindowDuration();
        Duration advanceSize = Duration.ofSeconds(30);

        log.info("Starting stream processing with window duration: {}s, advance interval: {}s, minimum duration: {}s, minimum pings: {}, discount rate: {}",
            windowSize.getSeconds(),
            advanceSize.getSeconds(),
            config.getCalculatedMinDuration().getSeconds(),
            config.getMinPings(),
            config.getDiscountRate());

        KStream<String, PagePingEvent> inputStream = builder
            .stream(config.getInputTopic(),
                Consumed.with(Serdes.String(), eventSerde)
                       .withTimestampExtractor(new EventTimestampExtractor()))
            .peek((key, value) -> log.debug("Received event: key={}, timestamp={}", 
                key, value.getCollectorTimestamp()))
            .selectKey((ignoredKey, event) -> event.getUserId());

        Materialized<String, ArrayList<PagePingEvent>, WindowStore<Bytes, byte[]>> materialized =
            Materialized.<String, ArrayList<PagePingEvent>, WindowStore<Bytes, byte[]>>as("page-views-store")
                .withKeySerde(Serdes.String())
                .withValueSerde(pagePingEventListSerde)
                .withRetention(Duration.ofDays(1));

        ContinuousViewProcessor continuousViewProcessor = new ContinuousViewProcessor();
        continuousViewProcessor.addProcessing(inputStream, windowSize, advanceSize, materialized);

        // TODO: Add MostViewedProcessor here

        final KafkaStreams streams = new KafkaStreams(builder.build(), props);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down streams...");
            streams.close(Duration.ofSeconds(10));
        }));

        streams.setUncaughtExceptionHandler(exception -> {
            log.error("Uncaught error in streams", exception);
            return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });

        streams.cleanUp();
        
        log.info("Starting Kafka Streams...");
        streams.start();
    }

    public static void main(String[] args) {
        new DiscountsProcessor().start();
    }
}