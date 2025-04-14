package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
public class ContinuousViewProcessor extends ProcessWindowFunction<PagePingEvent, DiscountEvent, String, TimeWindow> {
    private final ConfigurationManager config = ConfigurationManager.getInstance();

    @Override
    public void process(
            String userId,
            Context context,
            Iterable<PagePingEvent> elements,
            Collector<DiscountEvent> out) throws Exception {

        TimeWindow window = context.window();
        log.debug("Starting window processing for user: {} - Window: {} to {}",
                userId,
                Instant.ofEpochMilli(window.getStart()),
                Instant.ofEpochMilli(window.getEnd()));

        Map<String, List<PagePingEvent>> eventsByProduct = StreamSupport
            .stream(elements.spliterator(), false)
            .sorted(Comparator.comparing(PagePingEvent::getCollectorTimestamp))
            .collect(Collectors.groupingBy(PagePingEvent::getWebpageId));

        for (Map.Entry<String, List<PagePingEvent>> entry : eventsByProduct.entrySet()) {
            String webpageId = entry.getKey();
            List<PagePingEvent> productEvents = entry.getValue();

            if (productEvents.isEmpty()) continue;

            // Encontrar o primeiro evento product_view
            Optional<PagePingEvent> firstProductView = productEvents.stream()
                .filter(e -> "product_view".equals(e.getEventName()))
                .findFirst();

            if (firstProductView.isEmpty()) {
                log.debug("Skipping webpage {} - no product_view event found", webpageId);
                continue;
            }

            PagePingEvent productViewEvent = firstProductView.get();
            String productId = ((ProductViewEvent) productViewEvent).getProductId();

            // Contar page_pings após o product_view
            long pagePingCount = productEvents.stream()
                .filter(e -> "page_ping".equals(e.getEventName()))
                .filter(e -> e.getCollectorTimestamp().isAfter(productViewEvent.getCollectorTimestamp()))
                .count();

            if (pagePingCount < config.getMinPings()) {
                log.debug("Skipping product {} - insufficient page_pings (got: {}, needed: {})",
                    productId, pagePingCount, config.getMinPings());
                continue;
            }

            // Encontrar o último page_ping
            Optional<PagePingEvent> lastPing = productEvents.stream()
                .filter(e -> "page_ping".equals(e.getEventName()))
                .max(Comparator.comparing(PagePingEvent::getCollectorTimestamp));

            if (!lastPing.isPresent()) continue;

            Duration viewDuration = Duration.between(
                productViewEvent.getCollectorTimestamp(),
                lastPing.get().getCollectorTimestamp()
            );

            if (viewDuration.compareTo(config.getCalculatedMinDuration()) < 0) {
                log.debug("Skipping product {} - view duration too short (got: {}s, needed: {}s)",
                    productId, viewDuration.getSeconds(), 
                    config.getCalculatedMinDuration().getSeconds());
                continue;
            }

            log.info("Generating discount for user {} on product {} (duration: {}s, pings: {})",
                userId, productId, viewDuration.getSeconds(), pagePingCount);

            out.collect(DiscountEvent.createContinuousViewDiscount(
                userId,
                productId,
                viewDuration.toSeconds()
            ));
        }
    }
}