package com.example.processor;

import com.example.model.PagePingEvent;
import com.example.model.DiscountEvent;
import lombok.extern.slf4j.Slf4j;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import java.util.Optional;

@Slf4j
public class MostViewedProcessor extends ProcessWindowFunction<PagePingEvent, DiscountEvent, String, TimeWindow> {
    private static final int MIN_VIEWS_THRESHOLD = 3;
    private static final long COOLDOWN_MILLIS = Duration.ofMinutes(5).toMillis();
    
    private ValueState<Long> lastDiscountTimestamp;

    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>(
            "last-discount-timestamp",
            Long.class
        );
        lastDiscountTimestamp = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void process(
        String userId,
        Context context,
        Iterable<PagePingEvent> events,
        Collector<DiscountEvent> out
    ) throws Exception {
        Long lastDiscount = lastDiscountTimestamp.value();
        long windowStart = context.window().getStart();

        if (lastDiscount != null) {
            long timeSinceLastDiscount = windowStart - lastDiscount;
            
            log.debug("Cooldown check - Last discount: {}, Window start: {}, Time since last: {}ms, Cooldown period: {}ms",
                     lastDiscount, windowStart, timeSinceLastDiscount, COOLDOWN_MILLIS);
            
            if (timeSinceLastDiscount < COOLDOWN_MILLIS) {
                log.debug("Skipping due to cooldown period. Remaining: {}ms", 
                         COOLDOWN_MILLIS - timeSinceLastDiscount);
                return;
            }
        }

        Map<String, ProductStats> productStats = new HashMap<>();
        for (PagePingEvent event : events) {
            productStats.computeIfAbsent(event.getWebpageId(), k -> new ProductStats())
                       .addEvent(event);
        }

        Optional<Map.Entry<String, ProductStats>> mostViewed = productStats.entrySet().stream()
            .filter(e -> e.getValue().getViews() >= MIN_VIEWS_THRESHOLD)
            .max((a, b) -> a.getValue().compareTo(b.getValue()));

        if (mostViewed.isPresent()) {
            String productId = mostViewed.get().getKey();
            ProductStats stats = mostViewed.get().getValue();
            
            DiscountEvent discount = DiscountEvent.createMostViewedDiscount(
                userId,
                productId,
                stats.getViews(),
                stats.getDurationSeconds()
            );

            lastDiscountTimestamp.update(context.window().maxTimestamp());
            out.collect(discount);
        }
    }

    private static class ProductStats implements Comparable<ProductStats> {
        private int views = 0;
        private long firstEventTime = Long.MAX_VALUE;
        private long lastEventTime = Long.MIN_VALUE;

        public void addEvent(PagePingEvent event) {
            views++;
            long eventTime = event.getCollectorTimestamp().toEpochMilli();
            firstEventTime = Math.min(firstEventTime, eventTime);
            lastEventTime = Math.max(lastEventTime, eventTime);
        }

        public int getViews() {
            return views;
        }

        public int getDurationSeconds() {
            return (int)((lastEventTime - firstEventTime) / 1000);
        }

        @Override
        public int compareTo(ProductStats other) {
            int viewComparison = Integer.compare(this.views, other.views);
            if (viewComparison != 0) {
                return viewComparison;
            }
            
            return Integer.compare(this.getDurationSeconds(), other.getDurationSeconds());
        }
    }
}
