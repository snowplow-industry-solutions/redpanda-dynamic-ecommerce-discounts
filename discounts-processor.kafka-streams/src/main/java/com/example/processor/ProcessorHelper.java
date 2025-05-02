package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ProcessorHelper {
  private final ConfigurationManager config = ConfigurationManager.getInstance();

  @Data
  @AllArgsConstructor
  public static class ProductSummary {
    private final String productId;
    private final String productName;
    private final int views;
    private final long durationInSeconds;
    private final List<Integer> pingCount;
    private final Instant lastView;
    private List<Long> delayToNextProduct;
  }

  @Data
  public static class ViewSummary {
    private final List<ProductSummary> products;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final String duration;
    private final long totalViewingTime;
    private final long totalRandomDelays;
    private final long totalTimeWithDelays;
  }

  @Getter
  @AllArgsConstructor
  public static class ProductViewSummary {
    private final List<PagePingEvent> events;
    private final long pingCount;
    private final Optional<ProductViewEvent> productView;
  }

  public ViewSummary summarizeViews(List<PagePingEvent> events) {
    Map<String, List<PagePingEvent>> eventsByProduct =
        events.stream()
            .collect(
                Collectors.groupingBy(
                    event -> {
                      if (event instanceof ProductViewEvent) {
                        return ((ProductViewEvent) event).getProductId();
                      }
                      return "product" + event.getWebpageId().replaceFirst("page", "");
                    }));

    List<ProductSummary> productSummaries = new ArrayList<>();

    Map<String, List<Long>> delaysByProduct = new HashMap<>();

    Instant lastProductEndTime = null;
    String lastProductId = null;
    long totalRandomDelays = 0;

    List<PagePingEvent> sortedEvents =
        events.stream()
            .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
            .collect(Collectors.toList());

    for (PagePingEvent event : sortedEvents) {
      String productId;
      if (event instanceof ProductViewEvent) {
        productId = ((ProductViewEvent) event).getProductId();
      } else {
        productId = "product" + event.getWebpageId().replaceFirst("page", "");
      }

      if (event instanceof ProductViewEvent
          && lastProductEndTime != null
          && lastProductId != null
          && !lastProductId.equals(productId)) {

        long timeDiff =
            Duration.between(lastProductEndTime, event.getCollectorTimestamp()).getSeconds();

        delaysByProduct.computeIfAbsent(lastProductId, k -> new ArrayList<>()).add(timeDiff);
        if (timeDiff > 0) {
          totalRandomDelays += timeDiff;
        }
      }

      lastProductId = productId;
      lastProductEndTime = event.getCollectorTimestamp();
    }

    for (String productId : eventsByProduct.keySet()) {
      List<PagePingEvent> productEvents = eventsByProduct.get(productId);
      Optional<ProductViewEvent> productView =
          productEvents.stream()
              .filter(ProductViewEvent.class::isInstance)
              .map(ProductViewEvent.class::cast)
              .findFirst();

      if (productView.isEmpty()) {
        continue;
      }

      List<Integer> pingCounts = calculatePingCountsPerView(productEvents);
      long totalPings = pingCounts.stream().mapToInt(Integer::intValue).sum();
      long durationInSeconds = totalPings > 0 ? config.calculateTotalViewingSeconds(totalPings) : 0;
      long views = productEvents.stream().filter(e -> e instanceof ProductViewEvent).count();

      ProductSummary summary =
          new ProductSummary(
              productId,
              productView.get().getProductName(),
              (int) views,
              durationInSeconds,
              pingCounts,
              productEvents.stream()
                  .map(PagePingEvent::getCollectorTimestamp)
                  .max(Instant::compareTo)
                  .orElse(Instant.now()),
              delaysByProduct.getOrDefault(productId, Collections.singletonList(0L)));

      productSummaries.add(summary);
    }

    productSummaries.sort(
        (a, b) -> {
          if (b.getDurationInSeconds() != a.getDurationInSeconds()) {
            return Long.compare(b.getDurationInSeconds(), a.getDurationInSeconds());
          }
          return b.getLastView().compareTo(a.getLastView());
        });

    long totalViewingTime =
        productSummaries.stream().mapToLong(ProductSummary::getDurationInSeconds).sum();

    long totalTimeWithDelays =
        totalViewingTime
            + delaysByProduct.values().stream()
                .flatMap(List::stream)
                .mapToLong(Long::longValue)
                .sum();

    String duration =
        String.format(
            "%02d:%02d:%02d",
            totalTimeWithDelays / 3600,
            (totalTimeWithDelays % 3600) / 60,
            totalTimeWithDelays % 60);

    return new ViewSummary(
        productSummaries,
        sortedEvents.stream()
            .map(PagePingEvent::getCollectorTimestamp)
            .min(Instant::compareTo)
            .orElse(Instant.now()),
        sortedEvents.stream()
            .map(PagePingEvent::getCollectorTimestamp)
            .max(Instant::compareTo)
            .orElse(Instant.now()),
        duration,
        totalViewingTime,
        totalRandomDelays,
        totalTimeWithDelays);
  }

  public Map<String, ProductViewSummary> calculateProductViewSummaries(List<PagePingEvent> events) {
    Map<String, List<PagePingEvent>> eventsByWebpageId =
        events.stream()
            .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
            .collect(Collectors.groupingBy(PagePingEvent::getWebpageId));

    return eventsByWebpageId.entrySet().stream()
        .filter(
            entry -> {
              List<PagePingEvent> productEvents = entry.getValue();
              long pingCount = countPagePings(productEvents);
              int minPings = config.getMinPingsForContinuousViewDiscount();

              log.info(
                  "Checking webpage {}: pingCount={}, minPings={}",
                  entry.getKey(),
                  pingCount,
                  minPings);

              if (pingCount < minPings) {
                log.info(
                    "Webpage {} has {} pings, less than minimum required ({})",
                    entry.getKey(),
                    pingCount,
                    minPings);
                return false;
              }

              Optional<PagePingEvent> productView =
                  productEvents.stream().filter(ProductViewEvent.class::isInstance).findFirst();

              log.info(
                  "Webpage {} has product view event: {}", entry.getKey(), productView.isPresent());
              return productView.isPresent();
            })
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                  List<PagePingEvent> productEvents = entry.getValue();
                  long pingCount = countPagePings(productEvents);
                  Optional<PagePingEvent> productView =
                      productEvents.stream().filter(ProductViewEvent.class::isInstance).findFirst();

                  return new ProductViewSummary(
                      productEvents, pingCount, productView.map(ProductViewEvent.class::cast));
                }));
  }

  protected Optional<DiscountEvent> processEvents(String userId, List<PagePingEvent> events) {
    log.info("Processing {} events for user {}", events.size(), userId);

    int index = 0;
    for (PagePingEvent event : events) {
      log.info(
          "Event[{}]: type={}, webpageId={}, timestamp={}",
          index++,
          event instanceof ProductViewEvent ? "ProductView" : "PagePing",
          event.getWebpageId(),
          event.getCollectorTimestamp());
      if (event instanceof ProductViewEvent) {
        ProductViewEvent pve = (ProductViewEvent) event;
        log.info(
            "   ProductView details: productId={}, productName={}, productPrice={}",
            pve.getProductId(),
            pve.getProductName(),
            pve.getProductPrice());
      }
    }

    Map<String, ProductViewSummary> productSummaries = calculateProductViewSummaries(events);

    log.info("Created {} product summaries", productSummaries.size());

    productSummaries.forEach(
        (webpage, summary) -> {
          log.info(
              "Summary for webpage {}: pingCount={}, minPings={}, hasProductView={}",
              webpage,
              summary.getPingCount(),
              config.getMinPingsForContinuousViewDiscount(),
              summary.getProductView().isPresent());
        });

    Optional<Map.Entry<String, ProductViewSummary>> productWithMostPings =
        productSummaries.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().getPingCount()
                        >= config.getMinPingsForContinuousViewDiscount())
            .max(Comparator.comparing(entry -> entry.getValue().getPingCount()));

    if (productWithMostPings.isEmpty()) {
      log.info("No product with sufficient pings found");
    } else {
      log.info(
          "Selected product with most pings: webpage={}, pingCount={}",
          productWithMostPings.get().getKey(),
          productWithMostPings.get().getValue().getPingCount());
    }

    return productWithMostPings.flatMap(
        entry -> {
          ProductViewSummary summary = entry.getValue();
          return summary
              .getProductView()
              .map(
                  productView -> {
                    long durationInSeconds =
                        config.calculateTotalViewingSeconds(summary.getPingCount());

                    log.info(
                        "Creating discount for user {} on product {} (pings: {}, duration: {}s)",
                        userId,
                        productView.getProductId(),
                        summary.getPingCount(),
                        durationInSeconds);

                    DiscountEvent discount =
                        DiscountEvent.createContinuousViewDiscount(
                            userId,
                            productView.getProductId(),
                            durationInSeconds,
                            config.getDiscountRate(),
                            System.currentTimeMillis());

                    log.info("Generated discount event: {}", discount);
                    return discount;
                  });
        });
  }

  public long countPagePings(List<PagePingEvent> events) {

    long countByType =
        events.stream().filter(event -> !(event instanceof ProductViewEvent)).count();

    long countByName =
        events.stream().filter(event -> "page_ping".equals(event.getEventName())).count();

    long finalCount = countByName > 0 ? countByName : countByType;

    log.info(
        "Counted {} page pings: {} by eventName, {} by !instanceof ProductViewEvent",
        finalCount,
        countByName,
        countByType);
    return finalCount;
  }

  private List<Integer> calculatePingCountsPerView(List<PagePingEvent> events) {
    List<Integer> pingCounts = new ArrayList<>();
    int currentCount = 0;

    List<PagePingEvent> sortedEvents =
        events.stream()
            .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
            .collect(Collectors.toList());

    boolean firstView = true;
    for (PagePingEvent event : sortedEvents) {
      if (event instanceof ProductViewEvent) {
        if (!firstView) {
          pingCounts.add(currentCount);
        }
        firstView = false;
        currentCount = 0;
      } else if (!"product_view".equals(event.getEventName())) {
        currentCount++;
      }
    }

    pingCounts.add(currentCount);

    return pingCounts;
  }
}
