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
public class ContinuousViewProcessor
    implements Processor<String, PagePingEvent, String, DiscountEvent> {
  private final ConfigurationManager config;
  private final ProcessorHelper processorHelper;

  private ProcessorContext<String, DiscountEvent> context;
  private KeyValueStore<String, Long> lastDiscountStore;
  private KeyValueStore<String, List<PagePingEvent>> eventsStore;
  private KeyValueStore<String, Long> startTimeStore;

  public ContinuousViewProcessor() {
    this.config = ConfigurationManager.getInstance();
    this.processorHelper = new ProcessorHelper();
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
        Duration.ofSeconds(config.getContinuousViewWindowCheckIntervalSeconds()),
        PunctuationType.WALL_CLOCK_TIME,
        this::checkAllWindows);
  }

  @Override
  public void process(Record<String, PagePingEvent> record) {
    try {
      String userId = record.key();
      PagePingEvent event = record.value();
      long currentTimestamp = record.timestamp();

      if (event instanceof ProductViewEvent) {
        ProductViewEvent productViewEvent = (ProductViewEvent) event;
        String windowKey = createWindowKey(userId, productViewEvent.getWebpageId());

        List<PagePingEvent> events = new ArrayList<>();
        events.add(productViewEvent);
        eventsStore.put(windowKey, events);
        startTimeStore.put(windowKey, currentTimestamp);

        log.debug(
            "Initialized window for key={} with ProductViewEvent at timestamp={}",
            windowKey,
            currentTimestamp);
      } else {
        String windowKey = createWindowKey(userId, event.getWebpageId());

        log.debug("Processing key(userId)={}, value(event)={}", userId, event);

        Long lastDiscountTime = lastDiscountStore.get(windowKey);
        if (lastDiscountTime != null && lastDiscountTime > currentTimestamp) {

          log.warn(
              "Detected invalid lastDiscountTime in future: lastDiscount={}, current={}. Cleaning window: {}",
              lastDiscountTime,
              currentTimestamp,
              windowKey);
          clearWindowState(windowKey);
          return;
        }

        if (!shouldProcessEvent(currentTimestamp, lastDiscountTime, windowKey)) {
          return;
        }

        if (lastDiscountTime != null && isInSameWindow(lastDiscountTime, currentTimestamp)) {
          log.debug(
              "Skipping processing - still in same window. Key={}, lastDiscount={}, current={}",
              windowKey,
              lastDiscountTime,
              currentTimestamp);
          return;
        }

        List<PagePingEvent> events = eventsStore.get(windowKey);
        if (events == null) {
          log.debug(
              "No ProductViewEvent found for window key={}, skipping PagePingEvent", windowKey);
          return;
        }

        events.add(event);
        eventsStore.put(windowKey, events);
      }
    } catch (Exception e) {
      log.error("Error processing record: {}", record, e);
    }
  }

  private void checkAllWindows(long windowTimestamp) {
    Map<String, List<PagePingEvent>> windowsWithSufficientPings = new HashMap<>();
    Map<String, Long> invalidWindows = new HashMap<>();
    Map<String, String> windowsToClean = new HashMap<>();

    if (config.showCheckingWindows()) {
      log.info(
          "Checking windows with settings: minPings={}, windowDurationMs={}",
          config.getMinPingsForContinuousViewDiscount(),
          config.getWindowDurationMs());
    }

    try (KeyValueIterator<String, List<PagePingEvent>> it = eventsStore.all()) {
      int totalWindows = 0;
      while (it.hasNext()) {
        totalWindows++;
        KeyValue<String, List<PagePingEvent>> entry = it.next();
        String windowKey = entry.key;
        List<PagePingEvent> events = entry.value;
        Long startTime = startTimeStore.get(windowKey);
        Long lastDiscountTime = lastDiscountStore.get(windowKey);

        if (events == null || events.isEmpty() || startTime == null) {
          log.debug(
              "Window {} skipped: events={}, startTime={}",
              windowKey,
              events == null ? "null" : events.size(),
              startTime);
          windowsToClean.put(windowKey, "Empty events or null startTime");
          continue;
        }

        if (lastDiscountTime != null && lastDiscountTime > windowTimestamp) {
          log.warn(
              "Found invalid lastDiscountTime (in future): lastDiscount={}, current={}. Window: {}",
              lastDiscountTime,
              windowTimestamp,
              windowKey);
          invalidWindows.put(windowKey, lastDiscountTime);
          continue;
        }

        long lastEventTime = events.get(events.size() - 1).getCollectorTimestamp().toEpochMilli();

        if (lastDiscountTime != null && isInSameWindow(lastDiscountTime, lastEventTime)) {
          continue;
        }

        ProductViewEvent productViewEvent = null;
        for (PagePingEvent event : events) {
          if (event instanceof ProductViewEvent) {
            productViewEvent = (ProductViewEvent) event;
            break;
          }
        }

        if (productViewEvent == null) {
          log.debug("No ProductViewEvent found in window={}", windowKey);
          windowsToClean.put(windowKey, "No ProductViewEvent found");
          continue;
        }

        log.info(
            "Window {} has ProductViewEvent with productId={}",
            windowKey,
            productViewEvent.getProductId());

        int pingCount = 0;
        for (PagePingEvent event : events) {
          if (!(event instanceof ProductViewEvent)) {
            long eventTime = event.getCollectorTimestamp().toEpochMilli();
            if ((eventTime - startTime) <= config.getWindowDurationMs()) {
              pingCount++;
            }
          }
        }

        long viewingTimeSeconds = config.calculateTotalViewingSeconds(pingCount);

        log.info(
            "Window {} has {} pings, viewing time: {}s, minimum required pings: {}",
            windowKey,
            pingCount,
            viewingTimeSeconds,
            config.getMinPingsForContinuousViewDiscount());

        if (pingCount >= config.getMinPingsForContinuousViewDiscount()) {
          log.info(
              "Window {} qualified with {} pings (viewing time: {}s)",
              windowKey,
              pingCount,
              viewingTimeSeconds);
          windowsWithSufficientPings.put(windowKey, events);
        } else {
          log.info(
              "Window {} did not qualify: {} pings < {} required",
              windowKey,
              pingCount,
              config.getMinPingsForContinuousViewDiscount());

          long timeSinceLastEvent = windowTimestamp - lastEventTime;
          boolean windowExpired = timeSinceLastEvent > config.getWindowDurationMs();

          int possibleAdditionalPings = 0;
          if (!windowExpired) {

            long remainingWindowTime = config.getWindowDurationMs() - (lastEventTime - startTime);
            possibleAdditionalPings =
                (int) (remainingWindowTime / (config.getPingIntervalSeconds() * 1000));
          }

          boolean canReachMinPings =
              (pingCount + possibleAdditionalPings)
                  >= config.getMinPingsForContinuousViewDiscount();

          if (windowExpired || !canReachMinPings) {
            log.info(
                "Cleaning window {}: expired={}, current pings={}, possible additional pings={}, can reach min={}",
                windowKey,
                windowExpired,
                pingCount,
                possibleAdditionalPings,
                canReachMinPings);
            windowsToClean.put(windowKey, "Cannot reach minimum ping count");
          }
        }
      }

      if (config.showCheckingWindows()) {
        log.info(
            "Found {} total windows, {} qualified windows",
            totalWindows,
            windowsWithSufficientPings.size());
      }
    }

    if (!invalidWindows.isEmpty()) {
      log.info("Cleaning {} invalid windows with future timestamps", invalidWindows.size());
      for (String windowKey : invalidWindows.keySet()) {
        clearWindowState(windowKey);
      }
    }

    if (!windowsToClean.isEmpty()) {
      log.info("Cleaning {} windows that will not qualify for discount", windowsToClean.size());
      for (String windowKey : windowsToClean.keySet()) {
        log.debug("Cleaning window {}: {}", windowKey, windowsToClean.get(windowKey));
        clearWindowState(windowKey);
      }
    }

    if (!windowsWithSufficientPings.isEmpty()) {
      Map<String, Integer> userPingCounts = new HashMap<>();

      for (Map.Entry<String, List<PagePingEvent>> entry : windowsWithSufficientPings.entrySet()) {
        String windowKey = entry.getKey();
        String userId = windowKey.split(":")[0];
        List<PagePingEvent> events = entry.getValue();

        int pingCount = (int) events.stream().filter(e -> !(e instanceof ProductViewEvent)).count();

        if (!userPingCounts.containsKey(userId) || pingCount > userPingCounts.get(userId)) {
          userPingCounts.put(userId, pingCount);
        }
      }

      log.info("Found {} users with qualified windows", userPingCounts.size());

      for (String userId : userPingCounts.keySet()) {
        String bestWindowKey = null;
        int maxPings = 0;

        for (Map.Entry<String, List<PagePingEvent>> entry : windowsWithSufficientPings.entrySet()) {
          String windowKey = entry.getKey();
          if (windowKey.startsWith(userId + ":")) {
            List<PagePingEvent> events = entry.getValue();
            int pingCount =
                (int) events.stream().filter(e -> !(e instanceof ProductViewEvent)).count();

            if (pingCount > maxPings) {
              maxPings = pingCount;
              bestWindowKey = windowKey;
            }
          }
        }

        if (bestWindowKey != null) {
          List<PagePingEvent> events = windowsWithSufficientPings.get(bestWindowKey);

          log.info("Selected window {} with {} pings for user {}", bestWindowKey, maxPings, userId);

          processorHelper
              .processEvents(userId, events)
              .ifPresent(
                  discountEvent -> {
                    Record<String, DiscountEvent> discountRecord =
                        new Record<>(userId, discountEvent, windowTimestamp);
                    context.forward(discountRecord);

                    log.info("Generated discount timestamp: {}", windowTimestamp);

                    try (KeyValueIterator<String, List<PagePingEvent>> it = eventsStore.all()) {
                      while (it.hasNext()) {
                        String windowKey = it.next().key;
                        if (windowKey.startsWith(userId + ":")) {
                          clearWindowState(windowKey);
                          lastDiscountStore.put(windowKey, windowTimestamp);
                        }
                      }
                    }
                  });
        } else {
          log.error("No best window found for user {} despite having qualified windows", userId);
        }
      }
    } else {
      if (config.showCheckingWindows()) {
        log.info("No windows qualified for discounts");
      }
    }
  }

  private void clearWindowState(String windowKey) {
    eventsStore.delete(windowKey);
    startTimeStore.delete(windowKey);
    lastDiscountStore.delete(windowKey);
  }

  private boolean shouldProcessEvent(
      Long currentTimestamp, Long lastDiscountTime, String windowKey) {
    if (lastDiscountTime == null) {
      return true;
    }

    if (lastDiscountTime > currentTimestamp) {
      log.error(
          "Detected event with timestamp ({}) before last processed discount ({}). "
              + "This could indicate data replay or out-of-order events. Skipping processing. Key: {}",
          currentTimestamp,
          lastDiscountTime,
          windowKey);
      return false;
    }

    long cooldownPeriod = config.getWindowDurationMs();
    if (currentTimestamp < lastDiscountTime + cooldownPeriod) {
      log.debug(
          "Not enough time has passed since last discount. Key={}, lastDiscount={}, current={}",
          windowKey,
          lastDiscountTime,
          currentTimestamp);
      return false;
    }

    return true;
  }

  private boolean isInSameWindow(long lastDiscountTime, long currentTime) {
    if (lastDiscountTime > currentTime) {
      log.warn(
          "Invalid lastDiscountTime (in future): lastDiscount={}, current={}",
          lastDiscountTime,
          currentTime);
      return false;
    }
    return currentTime < lastDiscountTime + config.getWindowDurationMs();
  }

  private String createWindowKey(String userId, String webpageId) {
    return userId + ":" + webpageId;
  }

  @Override
  public void close() {}
}
