package com.example;

import com.example.config.ConfigurationManager;
import com.example.model.PagePingEvent;
import com.example.processor.ContinuousViewProcessor;
import com.example.processor.DiscountEventSender;
import com.example.processor.MostViewedProcessor;
import com.example.processor.ProcessorHelper;
import com.example.serialization.DiscountEventSerde;
import com.example.serialization.EventSerde;
import com.example.serialization.EventTimestampExtractor;
import com.example.serialization.PagePingEventListSerde;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

@Slf4j
public class DiscountsProcessor {
  private static final String CLASS_NAME = DiscountsProcessor.class.getSimpleName();
  private final ConfigurationManager config = ConfigurationManager.getInstance();
  private KafkaStreams streams;
  private DiscountEventSender discountEventSender;
  private Thread discountEventSenderThread;

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
    config.logConfiguration();
    log.info("Initializing Discounts Processor...");

    if (!config.hasAnyProcessorEnabled()) {
      log.error("No processors are enabled. At least one processor must be enabled.");
      throw new IllegalStateException("No processors are enabled");
    }

    if (config.isDiscountEventSenderEnabled()) {
      discountEventSender = new DiscountEventSender();
      discountEventSenderThread = new Thread(discountEventSender);
      discountEventSenderThread.start();
      log.info("Started Discount Event Sender...");
    } else {
      log.info("Discount Event Sender is disabled");
    }

    StreamsBuilder builder = new StreamsBuilder();

    if (config.isMostViewedProcessorEnabled()) {
      StoreBuilder<KeyValueStore<String, Long>> lastDiscountStoreBuilder =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.MOST_VIEWED_LAST_DISCOUNT_STORE),
              Serdes.String(),
              Serdes.Long());
      builder.addStateStore(lastDiscountStoreBuilder);

      StoreBuilder<KeyValueStore<String, Integer>> viewsStoreBuilder =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.MOST_VIEWED_VIEWS_STORE),
              Serdes.String(),
              Serdes.Integer());
      builder.addStateStore(viewsStoreBuilder);

      StoreBuilder<KeyValueStore<String, Long>> durationStoreBuilder =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.MOST_VIEWED_DURATION_STORE),
              Serdes.String(),
              Serdes.Long());
      builder.addStateStore(durationStoreBuilder);
    }

    KStream<String, PagePingEvent> inputStream =
        builder.stream(
                config.getInputTopic(),
                Consumed.with(Serdes.String(), new EventSerde())
                    .withTimestampExtractor(new EventTimestampExtractor()))
            .peek(
                (key, value) ->
                    log.debug(
                        "Received event: key={}, timestamp={}", key, value.getCollectorTimestamp()))
            .selectKey((ignoredKey, event) -> event.getUserId());

    ProcessorHelper helper = new ProcessorHelper();

    if (config.isContinuousViewProcessorEnabled()) {
      log.info("Configuring ContinuousViewProcessor...");

      StoreBuilder<KeyValueStore<String, Long>> continuousViewLastDiscountStore =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(
                  ConfigurationManager.CONTINUOUS_VIEW_LAST_DISCOUNT_STORE),
              Serdes.String(),
              Serdes.Long());
      builder.addStateStore(continuousViewLastDiscountStore);

      StoreBuilder<KeyValueStore<String, ArrayList<PagePingEvent>>> continuousViewEventsStore =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.CONTINUOUS_VIEW_EVENTS_STORE),
              Serdes.String(),
              new PagePingEventListSerde());
      builder.addStateStore(continuousViewEventsStore);

      StoreBuilder<KeyValueStore<String, Long>> continuousViewStartTimeStore =
          Stores.keyValueStoreBuilder(
              Stores.persistentKeyValueStore(ConfigurationManager.CONTINUOUS_VIEW_START_TIME_STORE),
              Serdes.String(),
              Serdes.Long());
      builder.addStateStore(continuousViewStartTimeStore);

      inputStream
          .process(
              ContinuousViewProcessor::new,
              ConfigurationManager.CONTINUOUS_VIEW_LAST_DISCOUNT_STORE,
              ConfigurationManager.CONTINUOUS_VIEW_EVENTS_STORE,
              ConfigurationManager.CONTINUOUS_VIEW_START_TIME_STORE)
          .to(config.getOutputTopic(), Produced.with(Serdes.String(), new DiscountEventSerde()));
    }

    if (config.isMostViewedProcessorEnabled()) {
      log.info("Configuring MostViewedProcessor...");

      inputStream
          .process(
              MostViewedProcessor::new,
              ConfigurationManager.MOST_VIEWED_LAST_DISCOUNT_STORE,
              ConfigurationManager.MOST_VIEWED_VIEWS_STORE,
              ConfigurationManager.MOST_VIEWED_DURATION_STORE)
          .to(config.getOutputTopic(), Produced.with(Serdes.String(), new DiscountEventSerde()));
    }

    streams = new KafkaStreams(builder.build(), createKafkaProperties());

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
    if (discountEventSender != null && config.isDiscountEventSenderEnabled()) {
      discountEventSender.stop();
      try {
        discountEventSenderThread.join(5000);
      } catch (InterruptedException e) {
        log.warn("Interrupted while waiting for Discount Event Sender to stop", e);
      }
    }
  }

  public static void main(String[] args) {
    new DiscountsProcessor().start();
  }
}
