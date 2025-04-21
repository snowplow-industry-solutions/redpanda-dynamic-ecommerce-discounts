package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ContinuousViewProcessor2
    implements Processor<String, PagePingEvent, String, DiscountEvent> {
  private final ConfigurationManager config;
  private final ContinuousViewProcessorHelper helper;

  private ProcessorContext<String, DiscountEvent> context;
  private KeyValueStore<String, Long> lastDiscountStore;
  private KeyValueStore<String, List<PagePingEvent>> eventsStore;
  private KeyValueStore<String, Long> startTimeStore;

  public ContinuousViewProcessor2() {
    this.config = ConfigurationManager.getInstance();
    this.helper = new ContinuousViewProcessorHelper(config);
  }

  @Override
  public void init(ProcessorContext<String, DiscountEvent> context) {
    this.context = context;
    this.lastDiscountStore =
        context.getStateStore(ConfigurationManager.CONTINUOUS_VIEW_LAST_DISCOUNT_STORE);
    this.eventsStore = context.getStateStore(ConfigurationManager.CONTINUOUS_VIEW_EVENTS_STORE);
    this.startTimeStore =
        context.getStateStore(ConfigurationManager.CONTINUOUS_VIEW_START_TIME_STORE);

    context.schedule(
        Duration.ofSeconds(10), PunctuationType.WALL_CLOCK_TIME, this::checkAllWindows);
  }

  private void cleanupExpiredWindows(long timestamp) {
    try (KeyValueIterator<String, Long> it = startTimeStore.all()) {
      while (it.hasNext()) {
        KeyValue<String, Long> entry = it.next();
        String windowKey = entry.key;
        Long startTime = entry.value;

        if (startTime != null && (timestamp - startTime) >= config.getWindowDurationMs()) {
          clearWindowState(windowKey);
        }
      }
    }
  }

  @Override
  public void process(Record<String, PagePingEvent> record) {
    try {
      String userId = record.key();
      PagePingEvent event = record.value();
      long currentTimestamp = record.timestamp();
      String windowKey = helper.createWindowKey(userId, event.getWebpageId());

      log.debug("Processing key(userId)={}, value(event)={}", userId, event);

      Long lastDiscountTime = lastDiscountStore.get(windowKey);
      if (!helper.shouldProcessEvent(currentTimestamp, lastDiscountTime, windowKey)) {
        return;
      }

      if (lastDiscountTime != null && helper.isInSameWindow(lastDiscountTime, currentTimestamp)) {
        log.debug(
            "Skipping processing - still in same window. Key={}, lastDiscount={}, current={}",
            windowKey,
            lastDiscountTime,
            currentTimestamp);
        return;
      }

      if (startTimeStore.get(windowKey) == null) {
        startTimeStore.put(windowKey, currentTimestamp);
      }

      List<PagePingEvent> events = eventsStore.get(windowKey);
      if (events == null) {
        events = new ArrayList<>();
      }
      events.add(event);
      eventsStore.put(windowKey, events);

      processWindowIfReady(windowKey, currentTimestamp);

    } catch (Exception e) {
      log.error("Error processing record: {}", e.getMessage(), e);
      throw e;
    }
  }

  private void checkAllWindows(long timestamp) {
    try (KeyValueIterator<String, List<PagePingEvent>> it = eventsStore.all()) {
      while (it.hasNext()) {
        KeyValue<String, List<PagePingEvent>> entry = it.next();
        processWindowIfReady(entry.key, timestamp);
      }
    }
  }

  private void processWindowIfReady(String windowKey, long currentTimestamp) {
    List<PagePingEvent> events = eventsStore.get(windowKey);
    if (events == null || events.isEmpty()) {
      return;
    }

    Long startTime = startTimeStore.get(windowKey);
    if (startTime == null) {
      return;
    }

    long windowDuration = currentTimestamp - startTime;
    if (windowDuration >= config.getWindowDurationMs()
        && events.size() >= config.getMinPingsForContinuousViewDiscount()) {

      helper.processEventsAndCreateDiscount(
          windowKey,
          events.size() - 1L,
          currentTimestamp,
          discountRecord -> {
            context.forward(discountRecord);
            lastDiscountStore.put(windowKey, currentTimestamp);
            clearWindowState(windowKey);
          });
    }
  }

  private void clearWindowState(String windowKey) {
    eventsStore.delete(windowKey);
    startTimeStore.delete(windowKey);
  }

  @Override
  public void close() {}
}
