package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final ProcessorHelper processorHelper;
  private final ContinuousViewProcessorHelper continuousViewHelper;

  private ProcessorContext<String, DiscountEvent> context;
  private KeyValueStore<String, Long> lastDiscountStore;
  private KeyValueStore<String, List<PagePingEvent>> eventsStore;
  private KeyValueStore<String, Long> startTimeStore;

  public ContinuousViewProcessor2() {
    this.config = ConfigurationManager.getInstance();
    this.processorHelper = new ProcessorHelper();
    this.continuousViewHelper = new ContinuousViewProcessorHelper(config);
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

  @Override
  public void process(Record<String, PagePingEvent> record) {
    try {
      String userId = record.key();
      PagePingEvent event = record.value();
      long currentTimestamp = record.timestamp();
      String windowKey = continuousViewHelper.createWindowKey(userId, event.getWebpageId());

      log.debug("Processing key(userId)={}, value(event)={}", userId, event);

      Long lastDiscountTime = lastDiscountStore.get(windowKey);
      if (!continuousViewHelper.shouldProcessEvent(currentTimestamp, lastDiscountTime, windowKey)) {
        return;
      }

      if (lastDiscountTime != null
          && continuousViewHelper.isInSameWindow(lastDiscountTime, currentTimestamp)) {
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
      log.error("Error processing record: {}", record, e);
    }
  }

  private void checkAllWindows(long timestamp) {
    Map<String, List<PagePingEvent>> userEvents = new HashMap<>();

    try (KeyValueIterator<String, List<PagePingEvent>> it = eventsStore.all()) {
      while (it.hasNext()) {
        KeyValue<String, List<PagePingEvent>> entry = it.next();
        String windowKey = entry.key;
        String userId = windowKey.split(":")[0];
        List<PagePingEvent> events = entry.value;

        if (events != null && !events.isEmpty()) {
          Long lastDiscountTime = lastDiscountStore.get(windowKey);
          long eventTimestamp =
              events.get(events.size() - 1).getCollectorTimestamp().toEpochMilli();

          if (lastDiscountTime == null) {
            userEvents.computeIfAbsent(userId, k -> new ArrayList<>()).addAll(events);
            continue;
          }

          if (continuousViewHelper.shouldProcessEvent(eventTimestamp, lastDiscountTime, windowKey)
              && !continuousViewHelper.isInSameWindow(lastDiscountTime, eventTimestamp)) {
            userEvents.computeIfAbsent(userId, k -> new ArrayList<>()).addAll(events);
          }
        }
      }
    }

    for (Map.Entry<String, List<PagePingEvent>> entry : userEvents.entrySet()) {
      String userId = entry.getKey();
      List<PagePingEvent> events = entry.getValue();

      processorHelper
          .processEvents(userId, events)
          .ifPresent(
              discountEvent -> {
                Record<String, DiscountEvent> discountRecord =
                    new Record<>(userId, discountEvent, timestamp);
                context.forward(discountRecord);

                try (KeyValueIterator<String, List<PagePingEvent>> it = eventsStore.all()) {
                  while (it.hasNext()) {
                    String windowKey = it.next().key;
                    if (windowKey.startsWith(userId + ":")) {
                      clearWindowState(windowKey);
                      lastDiscountStore.put(windowKey, timestamp);
                    }
                  }
                }
              });
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

    long pingCount = events.stream().filter(e -> !(e instanceof ProductViewEvent)).count();

    if (windowDuration >= config.getWindowDurationMs()
        && pingCount >= config.getMinPingsForContinuousViewDiscount()) {

      continuousViewHelper.processEventsAndCreateDiscount(
          windowKey,
          pingCount,
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
    lastDiscountStore.delete(windowKey);
  }

  @Override
  public void close() {}
}
