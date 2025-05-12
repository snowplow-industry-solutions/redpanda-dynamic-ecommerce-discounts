package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.example.processor.ProcessorHelper.ProductSummary;
import com.example.processor.ProcessorHelper.ViewSummary;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class MostViewedProcessor
    implements Processor<String, PagePingEvent, String, DiscountEvent> {
  private final ConfigurationManager config = ConfigurationManager.getInstance();
  private final ProcessorHelper processorHelper = new ProcessorHelper();

  private ProcessorContext<String, DiscountEvent> context;
  private KeyValueStore<String, Long> lastDiscountStore;
  private KeyValueStore<String, Integer> viewsStore;
  private KeyValueStore<String, Long> durationStore;

  private final Map<String, List<PagePingEvent>> tempEventCache = new HashMap<>();

  @Override
  public void init(ProcessorContext<String, DiscountEvent> context) {
    this.context = context;
    this.lastDiscountStore =
        context.getStateStore(ConfigurationManager.MOST_VIEWED_LAST_DISCOUNT_STORE);
    this.viewsStore = context.getStateStore(ConfigurationManager.MOST_VIEWED_VIEWS_STORE);
    this.durationStore = context.getStateStore(ConfigurationManager.MOST_VIEWED_DURATION_STORE);

    context.schedule(
        Duration.ofSeconds(config.getMostViewedWindowCheckIntervalSeconds()),
        PunctuationType.WALL_CLOCK_TIME,
        this::checkAllWindows);
  }

  @Override
  public void process(Record<String, PagePingEvent> record) {
    try {
      String userId = record.key();
      PagePingEvent event = record.value();
      long currentTimestamp = record.timestamp();
      long systemTime = System.currentTimeMillis();

      if (userId == null || event == null) {
        log.error("Invalid record: userId={}, event={}", userId, event);
        return;
      }

      log.debug(
          "Processing event: user={}, webpage={}, timestamp={}, systemTime={}",
          userId,
          event.getWebpageId(),
          currentTimestamp,
          systemTime);

      Long lastDiscountTime = lastDiscountStore.get(userId);

      if (lastDiscountTime == null
          || (systemTime - lastDiscountTime) >= config.getWindowDurationMs()) {
        tempEventCache.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);

        String viewKey = createKey(userId, event.getWebpageId());

        if (event instanceof ProductViewEvent) {
          ProductViewEvent pve = (ProductViewEvent) event;
          log.info(
              "Added ProductViewEvent to cache: user={}, product={}, webpage={}",
              userId,
              pve.getProductId(),
              pve.getWebpageId());

          Integer currentViews = viewsStore.get(viewKey);
          int newViews = currentViews == null ? 1 : currentViews + 1;
          viewsStore.put(viewKey, newViews);
          log.debug("Updated view count for key {}: {} -> {}", viewKey, currentViews, newViews);

          Long startTime = durationStore.get(viewKey);
          if (startTime == null) {
            durationStore.put(viewKey, currentTimestamp);
            log.debug("Registered first timestamp for key {}: {}", viewKey, currentTimestamp);
          }
        } else {
          log.info(
              "Added PagePingEvent to cache: user={}, webpage={}", userId, event.getWebpageId());
        }
      } else {
        log.debug(
            "Skipping - still in cooldown period. User={}, lastDiscount={}, current={}, system={}",
            userId,
            lastDiscountTime,
            currentTimestamp,
            systemTime);
      }

    } catch (Exception e) {
      log.error("Error processing record: {}", e.getMessage(), e);
    }
  }

  private void checkAllWindows(long timestamp) {
    if (config.showCheckingWindows()) {
      log.info("Running scheduled window check at timestamp {}", timestamp);
    }

    if (tempEventCache.isEmpty()) {
      if (config.showCheckingWindows()) {
        log.info("Event cache is empty, nothing to process");
      }
      return;
    }

    log.info("Cache contains data for {} users", tempEventCache.size());

    new ArrayList<>(tempEventCache.entrySet())
        .forEach(
            entry -> {
              String userId = entry.getKey();
              List<PagePingEvent> events = entry.getValue();

              try {
                processUserWindow(userId, events, timestamp);
              } catch (Exception e) {
                log.error("Error processing window for user {}: {}", userId, e.getMessage(), e);
              }
            });
  }

  private void processUserWindow(String userId, List<PagePingEvent> events, long timestamp) {
    Long lastDiscountTime = lastDiscountStore.get(userId);
    if (lastDiscountTime != null && isInSameWindow(lastDiscountTime, timestamp)) {
      log.info("User {} still in discount window, skipping", userId);
      return;
    }

    if (!events.isEmpty()) {
      log.info("Processing {} events for user {}", events.size(), userId);
      processUserEvents(userId, new ArrayList<>(events), timestamp);
    }
  }

  private void processUserEvents(String userId, List<PagePingEvent> events, long timestamp) {
    if (events.isEmpty()) {
      log.info("No events to process for user {}", userId);
      return;
    }

    if (timestamp <= 0) {
      log.error("Invalid timestamp {} for user {}", timestamp, userId);
      return;
    }

    long productViewCount = events.stream().filter(e -> e instanceof ProductViewEvent).count();

    log.info(
        "Processing {} events for user {} ({} are ProductViewEvents)",
        events.size(),
        userId,
        productViewCount);

    if (productViewCount == 0) {
      log.info("No ProductViewEvents found for user {}, skipping", userId);
      return;
    }

    ViewSummary viewSummary = processorHelper.summarizeViews(events);
    List<ProductSummary> products = viewSummary.getProducts();

    log.info(
        "ViewSummary for user {}: {} products, total viewing time: {}s",
        userId,
        products.size(),
        viewSummary.getTotalViewingTime());

    if (products.isEmpty()) {
      log.info("No products found in summary for user {}", userId);
      return;
    }

    List<ProductSummary> eligibleProducts =
        products.stream()
            .filter(p -> p.getViews() >= config.getMinViewsForMostViewedDiscount())
            .collect(Collectors.toList());

    if (eligibleProducts.isEmpty()) {
      log.info(
          "No products meet the criteria for user {}: min views={}",
          userId,
          config.getMinViewsForMostViewedDiscount());
      clearUserState(userId);
      return;
    }

    for (ProductSummary product : products) {
      log.info(
          "Product summary: id={}, name={}, views={}, seconds={}, pings={}",
          product.getProductId(),
          product.getProductName(),
          product.getViews(),
          product.getDurationInSeconds(),
          product.getPingCount());
    }

    ProductSummary mostViewedProduct = eligibleProducts.get(0);

    log.info(
        "Most viewed product for user {}: {} ({}) with {} views, {}s duration and {} pings",
        userId,
        mostViewedProduct.getProductId(),
        mostViewedProduct.getProductName(),
        mostViewedProduct.getViews(),
        mostViewedProduct.getDurationInSeconds(),
        mostViewedProduct.getPingCount());

    DiscountEvent discount =
        DiscountEvent.createMostViewedDiscount(
            userId,
            mostViewedProduct.getProductId(),
            mostViewedProduct.getViews(),
            mostViewedProduct.getDurationInSeconds(),
            config.getDiscountRate(),
            timestamp);

    Record<String, DiscountEvent> discountRecord = new Record<>(userId, discount, timestamp);
    context.forward(discountRecord);

    log.info(
        "Generated and forwarded most-viewed discount for user {} on product {}",
        userId,
        mostViewedProduct.getProductId());

    long systemTime = System.currentTimeMillis();
    lastDiscountStore.put(userId, systemTime);

    log.info("Updated last discount time for user {} to {}", userId, systemTime);

    clearUserState(userId);
  }

  private void clearUserState(String userId) {
    log.info("Clearing state for user {}", userId);

    String prefix = userId + ":";
    int keysRemoved = 0;

    try (KeyValueIterator<String, Integer> viewsIt = viewsStore.all()) {
      List<String> keysToRemove = new ArrayList<>();
      while (viewsIt.hasNext()) {
        KeyValue<String, Integer> entry = viewsIt.next();
        if (entry.key.startsWith(prefix)) {
          keysToRemove.add(entry.key);
        }
      }
      keysToRemove.forEach(viewsStore::delete);
      keysRemoved = keysToRemove.size();
    }

    int durationKeysRemoved = 0;
    try (KeyValueIterator<String, Long> durationIt = durationStore.all()) {
      List<String> keysToRemove = new ArrayList<>();
      while (durationIt.hasNext()) {
        KeyValue<String, Long> entry = durationIt.next();
        if (entry.key.startsWith(prefix)) {
          keysToRemove.add(entry.key);
        }
      }
      keysToRemove.forEach(durationStore::delete);
      durationKeysRemoved = keysToRemove.size();
    }

    log.info(
        "Removed {} view keys and {} duration keys for user {}",
        keysRemoved,
        durationKeysRemoved,
        userId);

    List<PagePingEvent> removedEvents = tempEventCache.remove(userId);
    log.info(
        "Removed {} events from cache for user {}",
        removedEvents != null ? removedEvents.size() : 0,
        userId);
  }

  private boolean isInSameWindow(long lastDiscountTime, long currentTime) {
    long systemTime = System.currentTimeMillis();
    if (lastDiscountTime > systemTime) {
      log.warn(
          "Last discount time {} is in the future compared to system time {}. Treating as not in same window.",
          lastDiscountTime,
          systemTime);
      return false;
    }

    long effectiveCurrentTime = Math.max(currentTime, systemTime);
    boolean result = effectiveCurrentTime < lastDiscountTime + config.getWindowDurationMs();

    if (result) {
      log.debug(
          "Current time {} is within window of last discount {} + {}ms",
          effectiveCurrentTime,
          lastDiscountTime,
          config.getWindowDurationMs());
    }
    return result;
  }

  private String createKey(String userId, String webpageId) {
    return userId + ":" + webpageId;
  }

  @Override
  public void close() {}
}
