package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import com.example.serialization.DiscountEventSerde;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.state.SessionStore;

@Slf4j
public class ContinuousViewProcessor {
  private final ConfigurationManager config = ConfigurationManager.getInstance();

  public KStream<Windowed<String>, DiscountEvent> addProcessing(
      KStream<String, PagePingEvent> inputStream,
      Materialized<String, ArrayList<PagePingEvent>, SessionStore<Bytes, byte[]>> materialized) {

    KStream<Windowed<String>, DiscountEvent> outputStream =
        inputStream
            .groupByKey()
            .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(30)))
            .aggregate(
                ArrayList::new,
                (userId, event, accumulator) -> {
                  accumulator.add(event);
                  return accumulator;
                },
                (aggKey, aggOne, aggTwo) -> {
                  aggOne.addAll(aggTwo);
                  return aggOne;
                },
                materialized)
            .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
            .toStream()
            .peek(
                (key, events) ->
                    log.info(
                        "Session closed for user {}: {} events collected in window {} to {}, first event: {}, last event: {}",
                        key.key(),
                        events.size(),
                        key.window().startTime(),
                        key.window().endTime(),
                        events.isEmpty() ? "none" : events.get(0).getCollectorTimestamp(),
                        events.isEmpty()
                            ? "none"
                            : events.get(events.size() - 1).getCollectorTimestamp()))
            .flatMapValues((key, windowEvents) -> processEvents(key.key(), windowEvents));

    outputStream.to(
        config.getOutputTopic(),
        Produced.<Windowed<String>, DiscountEvent>with(
            new WindowedSerdes.SessionWindowedSerde<>(Serdes.String()), new DiscountEventSerde()));

    return outputStream;
  }

  private List<DiscountEvent> processEvents(String userId, List<PagePingEvent> events) {
    List<DiscountEvent> discounts = new ArrayList<>();

    log.debug("Processing {} events for user {}", events.size(), userId);

    Map<String, List<PagePingEvent>> eventsByProduct =
        events.stream()
            .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
            .collect(Collectors.groupingBy(PagePingEvent::getWebpageId));

    log.debug("User {} viewed {} distinct products", userId, eventsByProduct.size());

    Optional<Map.Entry<String, List<PagePingEvent>>> productWithMostPings =
        eventsByProduct.entrySet().stream()
            .filter(
                entry -> {
                  List<PagePingEvent> productEvents = entry.getValue();
                  int pingCount = productEvents.size();

                  if (pingCount < config.getMinPings()) {
                    return false;
                  }

                  Optional<PagePingEvent> productView =
                      productEvents.stream().filter(e -> e instanceof ProductViewEvent).findFirst();

                  Optional<PagePingEvent> lastPing =
                      productEvents.stream()
                          .max(Comparator.comparing(PagePingEvent::getCollectorTimestamp));

                  if (productView.isPresent() && lastPing.isPresent()) {
                    Duration viewDuration =
                        Duration.between(
                            productView.get().getCollectorTimestamp(),
                            lastPing.get().getCollectorTimestamp());
                    return viewDuration.compareTo(config.getCalculatedMinDuration()) >= 0;
                  }
                  return false;
                })
            .max(Comparator.comparing(entry -> entry.getValue().size()));

    if (productWithMostPings.isPresent()) {
      String webpageId = productWithMostPings.get().getKey();
      List<PagePingEvent> productEvents = productWithMostPings.get().getValue();

      Optional<PagePingEvent> productView =
          productEvents.stream().filter(e -> e instanceof ProductViewEvent).findFirst();

      Optional<PagePingEvent> lastPing =
          productEvents.stream().max(Comparator.comparing(PagePingEvent::getCollectorTimestamp));

      if (productView.isPresent() && lastPing.isPresent()) {
        ProductViewEvent event = (ProductViewEvent) productView.get();
        Duration viewDuration =
            Duration.between(
                productView.get().getCollectorTimestamp(), lastPing.get().getCollectorTimestamp());

        log.info(
            "Generating discount for user {} on product {} (duration: {}s, pings: {})",
            userId,
            event.getProductId(),
            viewDuration.getSeconds(),
            productEvents.size());

        discounts.add(
            DiscountEvent.createContinuousViewDiscount(
                userId, event.getProductId(), viewDuration.toSeconds(), config.getDiscountRate()));
      }
    }

    log.debug("Generated {} discounts for user {}", discounts.size(), userId);
    return discounts;
  }
}
