package com.example.processor;

import com.example.model.PagePingEvent;
import com.example.model.DiscountEvent;
import com.example.config.ConfigurationManager;
import lombok.extern.slf4j.Slf4j;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

@Slf4j
public class ContinuousViewProcessor extends ProcessWindowFunction<PagePingEvent, DiscountEvent, String, TimeWindow> {
    private ValueState<Long> lastDiscountTimestamp;
    private final ConfigurationManager config = ConfigurationManager.getInstance();

    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<Long> descriptor = new ValueStateDescriptor<>(
            "last-discount-timestamp",
            Long.class
        );
        lastDiscountTimestamp = getRuntimeContext().getState(descriptor);
        log.info("ContinuousViewProcessor initialized with minimum view time: {}s and cooldown: {}min", 
                 config.getContinuousViewWindowDuration().getSeconds(),
                 config.getDiscountCooldownPeriod().toMinutes());
    }

    @Override
    public void process(
            String key,
            Context context,
            Iterable<PagePingEvent> elements,
            Collector<DiscountEvent> out) throws Exception {

        long currentTime = context.currentWatermark();
        log.debug("Processing window for key: {} at watermark time: {}", key, currentTime);
        
        Long lastDiscount = lastDiscountTimestamp.value();
        log.debug("Retrieved last discount timestamp for key {}: {}", key, lastDiscount);
        
        if (lastDiscount != null) {
            long timeSinceLastDiscount = (currentTime - lastDiscount) / 1000 / 60;
            log.debug("Time since last discount for key {}: {}min", key, timeSinceLastDiscount);
            
            if (timeSinceLastDiscount < config.getDiscountCooldownPeriod().toMinutes()) {
                log.debug("Cooldown period still active for key {}. Time remaining: {}min", 
                    key, config.getDiscountCooldownPeriod().toMinutes() - timeSinceLastDiscount);
                return;
            }
            log.debug("Cooldown period expired for key {}. Proceeding with discount evaluation", key);
        } else {
            log.debug("No previous discount found for key {}. First time evaluation", key);
        }

        int pingCount = 0;
        PagePingEvent lastPagePingEvent = null;
        long firstEventTime = Long.MAX_VALUE;
        long lastEventTime = Long.MIN_VALUE;

        log.debug("Starting event analysis for continuous view detection. Key: {}", key);
        for (PagePingEvent pagePingEvent : elements) {
            pingCount++;
            lastPagePingEvent = pagePingEvent;
            
            long eventTime = pagePingEvent.getCollectorTimestamp().toEpochMilli();
            firstEventTime = Math.min(firstEventTime, eventTime);
            lastEventTime = Math.max(lastEventTime, eventTime);
            
            log.debug("Processing ping event {} for key {}. Event time: {}", 
                pingCount, key, eventTime);
        }

        int viewDurationSeconds = (int)((lastEventTime - firstEventTime) / 1000);
        log.debug("View duration calculated for key {}: {}s with {} pings", 
            key, viewDurationSeconds, pingCount);

        if (lastPagePingEvent != null && pingCount >= config.getContinuousViewMinPings() && viewDurationSeconds >= config.getContinuousViewWindowDuration().getSeconds()) {
            log.debug("Discount criteria met for key {}. Creating discount event", key);
            
            DiscountEvent discountEvent = DiscountEvent.createContinuousViewDiscount(
                lastPagePingEvent.getUserId(),
                lastPagePingEvent.getWebpageId(),
                viewDurationSeconds
            );
            
            lastDiscountTimestamp.update(currentTime);
            log.debug("Updated last discount timestamp for key {} to: {}", key, currentTime);
            
            log.info("Generated continuous view discount for key: {}, webpage: {}, duration: {}s",
                     key, lastPagePingEvent.getWebpageId(), viewDurationSeconds);
            out.collect(discountEvent);
        } else {
            log.debug("Discount criteria not met for key {}. Pings: {}, Duration: {}s", 
                key, pingCount, viewDurationSeconds);
        }
    }
}