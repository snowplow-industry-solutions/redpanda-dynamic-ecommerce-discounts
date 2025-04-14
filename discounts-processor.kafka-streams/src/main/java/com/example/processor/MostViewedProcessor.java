package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.PagePingEvent;
import com.example.model.DiscountEvent;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MostViewedProcessor implements Processor<String, PagePingEvent, String, DiscountEvent> {
    private static final Logger logger = LoggerFactory.getLogger(MostViewedProcessor.class);
    private final ConfigurationManager config = ConfigurationManager.getInstance();

    private ProcessorContext<String, DiscountEvent> context;
    private KeyValueStore<String, Long> lastDiscountStore;
    private KeyValueStore<String, Integer> viewsStore;
    private KeyValueStore<String, Long> durationStore;

    @Override
    public void init(ProcessorContext<String, DiscountEvent> context) {
        this.context = context;
        this.lastDiscountStore = context.getStateStore(ConfigurationManager.LAST_DISCOUNT_STORE);
        this.viewsStore = context.getStateStore(ConfigurationManager.VIEWS_STORE);
        this.durationStore = context.getStateStore(ConfigurationManager.DURATION_STORE);
        
        logger.info("MostViewedProcessor initialized");
        context.schedule(
            Duration.ofSeconds(10),
            PunctuationType.WALL_CLOCK_TIME,
            timestamp -> {
                logger.debug("Running scheduled window check at {}", timestamp);
                checkAllWindows(timestamp);
            }
        );
    }

    private void checkAllWindows(long timestamp) {
        try (KeyValueIterator<String, Long> it = lastDiscountStore.all()) {
            while (it.hasNext()) {
                KeyValue<String, Long> entry = it.next();
                String userId = entry.key;
                processWindowIfEnded(userId, timestamp);
            }
        }
    }

    @Override
    public void process(Record<String, PagePingEvent> record) {
        try {
            String userId = record.key();
            PagePingEvent event = record.value();
            long currentTimestamp = record.timestamp();

            logger.debug("Processing PagePingEvent: user={}, webpage={}, timestamp={}", 
                userId, event.getWebpageId(), currentTimestamp);

            Long lastDiscountTime = lastDiscountStore.get(userId);
            if (lastDiscountTime != null && isInSameWindow(lastDiscountTime, currentTimestamp)) {
                logger.debug("Skipping processing - still in same window as last discount. User={}, lastDiscount={}, current={}", 
                    userId, lastDiscountTime, currentTimestamp);
                return;
            }

            String viewKey = createKey(userId, event.getWebpageId());
            Integer currentViews = viewsStore.get(viewKey);
            viewsStore.put(viewKey, currentViews == null ? 1 : currentViews + 1);

            String durationKey = createKey(userId, event.getWebpageId());
            Long startTime = durationStore.get(durationKey);
            if (startTime == null) {
                durationStore.put(durationKey, currentTimestamp);
            }

            processWindowIfEnded(userId, currentTimestamp);
        } catch (Exception e) {
            logger.error("Error processing record: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processWindowIfEnded(String userId, long currentTimestamp) {
        Long lastDiscountTime = lastDiscountStore.get(userId);
        if (lastDiscountTime == null ||
                currentTimestamp >= lastDiscountTime + config.getWindowDurationMs()) {

            logger.debug("Processing window end. User={}, lastDiscount={}, current={}", 
                userId, lastDiscountTime, currentTimestamp);

            Map<String, Integer> productViews = new HashMap<>();
            Map<String, Long> productDurations = new HashMap<>();
            Set<String> userProducts = getUserProducts(userId);

            for (String productId : userProducts) {
                String viewKey = createKey(userId, productId);
                Integer views = viewsStore.get(viewKey);
                if (views != null) {
                    productViews.put(productId, views);
                }

                String durationKey = createKey(userId, productId);
                Long startTime = durationStore.get(durationKey);
                if (startTime != null) {
                    long duration = currentTimestamp - startTime;
                    productDurations.put(productId, duration);
                }
            }

            String mostViewedProduct = null;
            int maxViews = 0;
            long maxDuration = 0;

            for (Map.Entry<String, Integer> entry : productViews.entrySet()) {
                String productId = entry.getKey();
                int views = entry.getValue();
                long duration = productDurations.getOrDefault(productId, 0L);

                if (views > maxViews ||
                        (views == maxViews && duration > maxDuration)) {
                    mostViewedProduct = productId;
                    maxViews = views;
                    maxDuration = duration;
                }
            }

            if (mostViewedProduct != null && maxViews >= config.getMinViewsForDiscount()) {
                DiscountEvent discount = DiscountEvent.createMostViewedDiscount(
                        userId,
                        mostViewedProduct,
                        maxViews,
                        config.getWindowDurationSeconds(),
                        config.getDiscountRate()
                );

                Record<String, DiscountEvent> discountRecord = 
                    new Record<>(userId, discount, currentTimestamp);
                
                logger.debug("Forwarding discount event to topic. Record={}", discountRecord);
                context.forward(discountRecord);

                logger.info("Generated discount: user={}, product={}, views={}, duration={}s, rate={}",
                        userId, 
                        mostViewedProduct,
                        maxViews,
                        config.getWindowDurationSeconds(),
                        config.getDiscountRate());

                lastDiscountStore.put(userId, currentTimestamp);
                clearUserState(userId);
            } else {
                logger.debug("No discount generated. User={}, maxViews={}, minRequired={}", 
                    userId, maxViews, config.getMinViewsForDiscount());
            }
        }
    }

    private Set<String> getUserProducts(String userId) {
        Set<String> products = new HashSet<>();
        String prefix = userId + ":";

        try (KeyValueIterator<String, Integer> it = viewsStore.all()) {
            while (it.hasNext()) {
                KeyValue<String, Integer> next = it.next();
                String key = next.key;
                if (key.startsWith(prefix)) {
                    String productId = key.substring(prefix.length());
                    products.add(productId);
                }
            }
        }

        return products;
    }

    private void clearUserState(String userId) {
        String prefix = userId + ":";

        try (KeyValueIterator<String, Integer> it = viewsStore.all()) {
            while (it.hasNext()) {
                KeyValue<String, Integer> next = it.next();
                String key = next.key;
                if (key.startsWith(prefix)) {
                    viewsStore.delete(key);
                }
            }
        }

        try (KeyValueIterator<String, Long> it = durationStore.all()) {
            while (it.hasNext()) {
                KeyValue<String, Long> next = it.next();
                String key = next.key;
                if (key.startsWith(prefix)) {
                    durationStore.delete(key);
                }
            }
        }
    }

    private boolean isInSameWindow(long lastDiscountTime, long currentTime) {
        return currentTime < lastDiscountTime + config.getWindowDurationMs();
    }

    private String createKey(String userId, String productId) {
        return userId + ":" + productId;
    }


    @Override
    public void close() {
    }
}