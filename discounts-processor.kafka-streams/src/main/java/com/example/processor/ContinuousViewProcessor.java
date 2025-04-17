package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.example.serialization.DiscountEventSerde;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.TimeWindow;
import org.apache.kafka.streams.state.WindowStore;

@Slf4j
public class ContinuousViewProcessor {
  private final ConfigurationManager config = ConfigurationManager.getInstance();

  public static class WindowState {
    private final ArrayList<PagePingEvent> events = new ArrayList<>();
    private boolean discountGenerated = false;

    public void addEvent(PagePingEvent event) {
      events.add(event);
    }

    public List<PagePingEvent> getEvents() {
      return events;
    }

    public boolean isDiscountGenerated() {
      return discountGenerated;
    }

    public void setDiscountGenerated(boolean generated) {
      discountGenerated = generated;
    }
  }

  public KStream<String, DiscountEvent> addProcessing(
      KStream<String, PagePingEvent> inputStream,
      Materialized<String, WindowState, WindowStore<Bytes, byte[]>> materialized) {

    KStream<String, DiscountEvent> outputStream = inputStream
        .peek(
            (key, value) -> log.info(
                "Received event for user_id/web_page = {}/{}", key, value.getWebpageId()))
        .groupByKey()
        .windowedBy(TimeWindows.ofSizeWithNoGrace(config.getWindowDuration())
            .advanceBy(config.getWindowDuration()))
        .aggregate(
            WindowState::new,
            (key, value, aggregate) -> {
              long eventTimeMs = value.getCollectorTimestamp().toEpochMilli();
              long windowStartMs = eventTimeMs - (eventTimeMs % config.getWindowDuration().toMillis());
              long windowEndMs = windowStartMs + config.getWindowDuration().toMillis();

              Windowed<String> windowedKey = new Windowed<>(
                  key,
                  new TimeWindow(windowStartMs, windowEndMs));

              log.info("Processing window for key={}, window=[{} to {}], current state.discountGenerated={}",
                  key,
                  new Date(windowedKey.window().start()),
                  new Date(windowedKey.window().end()),
                  aggregate.isDiscountGenerated());

              if (aggregate.isDiscountGenerated()) {
                log.debug("Discount already generated for user {} in window [{} to {}], ignoring new events",
                    key,
                    new Date(windowedKey.window().start()),
                    new Date(windowedKey.window().end()));
                return aggregate;
              }
              aggregate.addEvent(value);
              log.info(
                  "Aggregating event for user_id {} in window [{} to {}], total events: {}",
                  key,
                  new Date(windowedKey.window().start()),
                  new Date(windowedKey.window().end()),
                  aggregate.getEvents().size());
              return aggregate;
            },
            materialized)
        .toStream()
        .selectKey((windowed, state) -> {
          log.info("Processing final window state for key={}, window=[{} to {}], events={}, discountGenerated={}",
              windowed.key(),
              new Date(windowed.window().start()),
              new Date(windowed.window().end()),
              state.getEvents().size(),
              state.isDiscountGenerated());
          return windowed.key();
        })
        .flatMapValues(
            (key, windowState) -> {
              if (windowState.isDiscountGenerated()) {
                log.debug("Discount already generated for user {} in this window", key);
                return Collections.emptySet();
              }

              Optional<DiscountEvent> discount = processEvents(key, windowState.getEvents());
              if (discount.isPresent()) {
                windowState.setDiscountGenerated(true);
                return Collections.singleton(discount.get());
              }
              return Collections.emptySet();
            });

    outputStream.peek(
        (key, discount) -> log.info(
            "Generated discount event: user_id={}, product_id={}, discount.rate={}",
            key,
            discount.getProductId(),
            discount.getDiscount().getRate()));

    outputStream.to(
        config.getOutputTopic(), Produced.with(Serdes.String(), new DiscountEventSerde()));

    return outputStream;
  }

  protected Optional<DiscountEvent> processEvents(String userId, List<PagePingEvent> events) {
    log.debug("Processing {} events for user {}", events.size(), userId);

    Map<String, List<PagePingEvent>> eventsByProduct = events.stream()
        .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
        .collect(Collectors.groupingBy(PagePingEvent::getWebpageId));

    log.debug("User {} viewed {} distinct products", userId, eventsByProduct.size());

    Optional<Map.Entry<String, List<PagePingEvent>>> productWithMostPings = eventsByProduct.entrySet().stream()
        .filter(
            entry -> {
              List<PagePingEvent> productEvents = entry.getValue();
              long pingCount = countPagePings(productEvents);
              int minPings = config.getMinPings();

              log.debug(
                  "Checking product {}: pingCount={}, minPings={}",
                  entry.getKey(),
                  pingCount,
                  minPings);

              if (pingCount < minPings) {
                log.debug(
                    "Product {} has {} pings, less than minimum required ({})",
                    entry.getKey(),
                    pingCount,
                    minPings);
                return false;
              }

              Optional<PagePingEvent> productView = productEvents.stream().filter(ProductViewEvent.class::isInstance)
                  .findFirst();

              log.debug(
                  "Product {} has product view event: {}",
                  entry.getKey(),
                  productView.isPresent());
              return productView.isPresent();
            })
        .max(Comparator.comparing(entry -> countPagePings(entry.getValue())));

    return productWithMostPings.flatMap(
        entry -> {
          List<PagePingEvent> productEvents = entry.getValue();
          return productEvents.stream()
              .filter(ProductViewEvent.class::isInstance)
              .findFirst()
              .map(
                  productView -> {
                    ProductViewEvent event = (ProductViewEvent) productView;
                    long pingCount = countPagePings(productEvents);
                    long durationInSeconds = pingCount * config.getPingIntervalSeconds();

                    log.info(
                        "Generating discount for user {} on product {} (pings: {}, duration: {}s)",
                        userId,
                        event.getProductId(),
                        pingCount,
                        durationInSeconds);

                    return DiscountEvent.createContinuousViewDiscount(
                        userId, event.getProductId(), durationInSeconds, config.getDiscountRate());
                  });
        });
  }

  private long countPagePings(List<PagePingEvent> events) {
    long count = events.stream().filter(event -> "page_ping".equals(event.getEventName())).count();
    log.debug("Counted {} page_ping events out of {} total events", count, events.size());
    return count;
  }
}
