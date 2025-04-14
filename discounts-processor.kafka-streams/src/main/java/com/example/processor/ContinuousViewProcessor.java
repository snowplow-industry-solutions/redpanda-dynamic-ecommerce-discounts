package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ContinuousViewProcessor {
    private final ConfigurationManager config = ConfigurationManager.getInstance();

    public void addProcessing(KStream<String, PagePingEvent> inputStream,
                              Duration windowSize,
                              Duration advanceSize,
                              Materialized<String, ArrayList<PagePingEvent>, WindowStore<Bytes, byte[]>> materialized) {

        inputStream
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(windowSize)
                        .advanceBy(advanceSize))
                .aggregate(
                        ArrayList::new,
                        (userId, event, accumulator) -> {
                            accumulator.add(event);
                            return accumulator;
                        },
                        materialized
                )
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .peek(
                        (key, events) ->
                                log.info("Window closed for user {}: {} events collected in window {} to {}, first event: {}, last event: {}",
                        key.key(),
                        events.size(),
                        key.window().startTime(),
                        key.window().endTime(),
                        events.isEmpty() ? "none" : events.get(0).getCollectorTimestamp(),
                        events.isEmpty() ? "none" : events.get(events.size() - 1).getCollectorTimestamp()))
                .flatMapValues((key, windowEvents) -> processEvents(key.key(), windowEvents));
    }

    private List<DiscountEvent> processEvents(String userId, List<PagePingEvent> events) {
        List<DiscountEvent> discounts = new ArrayList<>();

        log.debug("Processing {} events for user {}", events.size(), userId);

        Map<String, List<PagePingEvent>> eventsByProduct = events.stream()
                .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
                .collect(Collectors.groupingBy(PagePingEvent::getWebpageId));

        log.debug("User {} viewed {} distinct products", userId, eventsByProduct.size());

        for (Map.Entry<String, List<PagePingEvent>> entry : eventsByProduct.entrySet()) {
            String webpageId = entry.getKey();
            List<PagePingEvent> productEvents = entry.getValue();
            int pingCount = productEvents.size();

            log.debug("Analyzing product view: userId={}, webpageId={}, pingCount={}",
                    userId, webpageId, pingCount);

            if (pingCount >= config.getMinPings()) {
                Optional<PagePingEvent> productView = productEvents.stream()
                        .filter(e -> e instanceof ProductViewEvent)
                        .findFirst();

                Optional<PagePingEvent> lastPing = productEvents.stream()
                        .max(Comparator.comparing(PagePingEvent::getCollectorTimestamp));

                if (productView.isPresent()) {
                    Duration viewDuration = Duration.between(
                            productView.get().getCollectorTimestamp(),
                            lastPing.get().getCollectorTimestamp()
                    );

                    Duration expectedMinDuration = config.getCalculatedMinDuration();

                    log.debug("View duration for user {} on webpage {}: {}s (minimum expected: {}s)",
                            userId, webpageId, viewDuration.getSeconds(), expectedMinDuration.getSeconds());

                    if (viewDuration.compareTo(expectedMinDuration) >= 0) {
                        ProductViewEvent event = (ProductViewEvent) productView.get();

                        log.info("Generating discount for user {} on product {} (duration: {}s, pings: {})",
                                userId, event.getProductId(), viewDuration.getSeconds(), pingCount);

                        discounts.add(DiscountEvent.createContinuousViewDiscount(
                                userId,
                                event.getProductId(),
                                viewDuration.toSeconds(),
                                config.getDiscountRate()
                        ));
                    } else {
                        log.debug("View duration {}s is less than expected duration {}s for user {} on webpage {}",
                                viewDuration.getSeconds(), expectedMinDuration.getSeconds(),
                                userId, webpageId);
                    }
                }
            }
        }

        log.debug("Generated {} discounts for user {}", discounts.size(), userId);
        return discounts;
    }
}
