package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;

@Slf4j
@AllArgsConstructor
public class ContinuousViewProcessor {
  private final ConfigurationManager config = ConfigurationManager.getInstance();
  private final ContinuousViewProcessorHelper helper = new ContinuousViewProcessorHelper(config);
  private final ProcessorHelper processorHelper;
  private static final String LAST_DISCOUNT_STORE = "continuous-view-last-discount-store";

  public KStream<String, DiscountEvent> addProcessing(
      StreamsBuilder builder,
      KStream<String, PagePingEvent> inputStream,
      Materialized<String, WindowState, WindowStore<Bytes, byte[]>> materialized) {

    log.info("Starting ContinuousViewProcessor processing...");

    StoreBuilder<KeyValueStore<String, Long>> lastDiscountStoreBuilder =
        Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(LAST_DISCOUNT_STORE), Serdes.String(), Serdes.Long());

    builder.addStateStore(lastDiscountStoreBuilder);

    return inputStream
        .peek(
            (key, value) ->
                log.debug(
                    "Processing event for user={}, webpage={}, timestamp={}",
                    key,
                    value.getWebpageId(),
                    value.getCollectorTimestamp()))
        .groupBy(
            (userId, event) -> {
              String groupKey = helper.createWindowKey(userId, event.getWebpageId());
              log.debug("Grouping by key: {}", groupKey);
              return groupKey;
            })
        .windowedBy(TimeWindows.ofSizeWithNoGrace(config.getWindowDuration()))
        .aggregate(
            WindowState::new,
            (key, event, state) -> {
              long currentTimestamp = event.getCollectorTimestamp().toEpochMilli();

              if (!state.getEvents().isEmpty()) {
                long lastEventTime =
                    state
                        .getEvents()
                        .get(state.getEvents().size() - 1)
                        .getCollectorTimestamp()
                        .toEpochMilli();

                if (currentTimestamp < lastEventTime) {
                  log.warn(
                      "Detected out-of-order event. Current={}, Last={}. Key: {}. Skipping event.",
                      currentTimestamp,
                      lastEventTime,
                      key);
                  return state;
                }
              }

              if (state.getLastDiscountTimestamp() != null) {
                boolean shouldProcess =
                    helper.shouldProcessEvent(
                        currentTimestamp, state.getLastDiscountTimestamp(), key);

                if (!shouldProcess) {
                  return state;
                }
              }

              state.addEvent(event);
              return state;
            },
            materialized)
        .toStream()
        .filter(
            (key, state) ->
                state != null && !state.getEvents().isEmpty() && !state.isDiscountGenerated())
        .process(
            () ->
                new Processor<Windowed<String>, WindowState, String, DiscountEvent>() {
                  private ProcessorContext<String, DiscountEvent> context;
                  private KeyValueStore<String, Long> lastDiscountStore;

                  @Override
                  public void init(ProcessorContext<String, DiscountEvent> context) {
                    this.context = context;
                    this.lastDiscountStore = context.getStateStore(LAST_DISCOUNT_STORE);
                  }

                  @Override
                  public void process(Record<Windowed<String>, WindowState> record) {
                    WindowState state = record.value();
                    long currentTimestamp = System.currentTimeMillis();

                    long eventsSize = state.getEvents().size();
                    if (eventsSize < config.getMinPingsForContinuousViewDiscount()) {
                      return;
                    }

                    helper.processEventsAndCreateDiscount(
                        record.key().key(),
                        eventsSize,
                        currentTimestamp,
                        discountRecord -> {
                          context.forward(discountRecord);
                          lastDiscountStore.put(record.key().key(), currentTimestamp);
                          state.setDiscountGenerated(true);
                          state.setLastDiscountTimestamp(currentTimestamp);
                        });
                  }

                  @Override
                  public void close() {}
                },
            LAST_DISCOUNT_STORE);
  }
}
