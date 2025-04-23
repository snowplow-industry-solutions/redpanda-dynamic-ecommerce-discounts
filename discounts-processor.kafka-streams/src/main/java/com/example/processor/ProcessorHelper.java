package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ProcessorHelper {
  private final ConfigurationManager config = ConfigurationManager.getInstance();

  @Data
  public static class ProductSummary {
    private final String productId;
    private final String productName;
    private final int occurrences;
    private final long totalSeconds;
    private final int pingCount;
  }

  @Data
  public static class ViewSummary {
    private final List<ProductSummary> products;
    private final Instant windowStart;
    private final Instant windowEnd;
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
                      return event.getWebpageId().replaceFirst("page_", "");
                    }));

    List<ProductSummary> productSummaries =
        eventsByProduct.entrySet().stream()
            .map(
                entry -> {
                  String productId = entry.getKey();
                  List<PagePingEvent> productEvents = entry.getValue();

                  Optional<ProductViewEvent> productView =
                      productEvents.stream()
                          .filter(ProductViewEvent.class::isInstance)
                          .map(ProductViewEvent.class::cast)
                          .findFirst();

                  if (productView.isEmpty()) {
                    log.warn("No product view event found for product {}", productId);
                    return null;
                  }

                  long pingCount = countPagePings(productEvents);
                  long totalSeconds = pingCount * config.getPingIntervalSeconds();

                  return new ProductSummary(
                      productId,
                      productView.get().getProductName(),
                      1,
                      totalSeconds,
                      (int) pingCount);
                })
            .filter(summary -> summary != null)
            .sorted(Comparator.comparing(ProductSummary::getPingCount).reversed())
            .collect(Collectors.toList());

    Instant windowStart =
        events.stream()
            .map(PagePingEvent::getCollectorTimestamp)
            .min(Instant::compareTo)
            .orElse(Instant.now());

    Instant windowEnd =
        events.stream()
            .map(PagePingEvent::getCollectorTimestamp)
            .max(Instant::compareTo)
            .orElse(Instant.now());

    return new ViewSummary(productSummaries, windowStart, windowEnd);
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

    Map<String, ProductViewSummary> productSummaries = calculateProductViewSummaries(events);

    productSummaries.forEach(
        (webpage, summary) -> {
          log.info(
              "DEBUG - Summary for webpage {}: pingCount={}, minPings={}",
              webpage,
              summary.getPingCount(),
              config.getMinPingsForContinuousViewDiscount());
        });

    Optional<Map.Entry<String, ProductViewSummary>> productWithMostPings =
        productSummaries.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().getPingCount()
                        >= config.getMinPingsForContinuousViewDiscount())
            .max(Comparator.comparing(entry -> entry.getValue().getPingCount()));

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
    long count = events.stream().filter(event -> "page_ping".equals(event.getEventName())).count();
    log.info("Counted {} page_ping events out of {} total events", count, events.size());
    return count;
  }
}
