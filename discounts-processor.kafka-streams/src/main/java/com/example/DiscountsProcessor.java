package com.example;

import com.example.config.ConfigurationManager;
import com.example.model.PagePingEvent;
import com.example.processor.ContinuousViewProcessor;
import com.example.processor.MostViewedProcessor;
import com.example.serialization.ArrayListSerde;
import com.example.serialization.EventSerde;
import com.example.serialization.EventTimestampExtractor;
import com.example.serialization.PagePingEventListSerde;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Properties;
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
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

@Slf4j
public class DiscountsProcessor {
  private static final String CLASS_NAME = DiscountsProcessor.class.getSimpleName();
  private final ConfigurationManager config = ConfigurationManager.getInstance();
  private final Properties props;
  private final EventSerde eventSerde;
  private final PagePingEventListSerde pagePingEventListSerde;
  private KafkaStreams streams;

  public DiscountsProcessor() {
    this.props = createKafkaProperties();
    this.eventSerde = new EventSerde();
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

    if (!config.hasAnyProcessorEnabled()) {
      log.error("No processors are enabled. At least one processor must be enabled.");
      throw new IllegalStateException("No processors are enabled");
    }

    StreamsBuilder builder = new StreamsBuilder();
    Duration windowSize = config.getWindowDuration();
    Duration advanceSize = Duration.ofSeconds(30);

    if (config.isMostViewedProcessorEnabled()) {
      StoreBuilder<KeyValueStore<String, Long>> lastDiscountStoreBuilder =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.LAST_DISCOUNT_STORE),
              Serdes.String(),
              Serdes.Long());
      builder.addStateStore(lastDiscountStoreBuilder);

      StoreBuilder<KeyValueStore<String, Integer>> viewsStoreBuilder =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.VIEWS_STORE),
              Serdes.String(),
              Serdes.Integer());
      builder.addStateStore(viewsStoreBuilder);

      StoreBuilder<KeyValueStore<String, Long>> durationStoreBuilder =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.DURATION_STORE),
              Serdes.String(),
              Serdes.Long());
      builder.addStateStore(durationStoreBuilder);
    }

    KStream<String, PagePingEvent> inputStream =
        builder.stream(
                config.getInputTopic(),
                Consumed.with(Serdes.String(), eventSerde)
                    .withTimestampExtractor(new EventTimestampExtractor()))
            .peek(
                (key, value) ->
                    log.debug(
                        "Received event: key={}, timestamp={}", key, value.getCollectorTimestamp()))
            .selectKey((ignoredKey, event) -> event.getUserId());

    if (config.isContinuousViewProcessorEnabled()) {
      log.info("Configuring ContinuousViewProcessor...");
      Materialized<String, ArrayList<PagePingEvent>, SessionStore<Bytes, byte[]>> materialized =
          Materialized.<String, ArrayList<PagePingEvent>, SessionStore<Bytes, byte[]>>as(
                  "continuous-view-store")
              .withKeySerde(Serdes.String())
              .withValueSerde(new ArrayListSerde<>(PagePingEvent.class));

      ContinuousViewProcessor continuousViewProcessor = new ContinuousViewProcessor();
      continuousViewProcessor.addProcessing(inputStream, materialized);
    }

    if (config.isMostViewedProcessorEnabled()) {
      log.info("Configuring MostViewedProcessor...");
      inputStream.process(
          () -> new MostViewedProcessor(),
          ConfigurationManager.LAST_DISCOUNT_STORE,
          ConfigurationManager.VIEWS_STORE,
          ConfigurationManager.DURATION_STORE);
    }

    streams = new KafkaStreams(builder.build(), props);

    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

    streams.setUncaughtExceptionHandler(
        exception -> {
          log.error("Uncaught error in streams", exception);
          return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });

    streams.cleanUp();

    log.info("Starting {}...", CLASS_NAME);
    streams.start();
  }

  public void stop() {
    log.info("Shutting down {}...", CLASS_NAME);
    if (streams != null) {
      streams.close(Duration.ofSeconds(10));
    }
  }

  public static void main(String[] args) {
    new DiscountsProcessor().start();
  }
}
