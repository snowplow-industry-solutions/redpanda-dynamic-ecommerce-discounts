package com.example.processor;

import com.example.model.Discount;
import com.example.model.Event;
import lombok.extern.slf4j.Slf4j;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

@Slf4j
public class ContinuousViewProcessor extends ProcessWindowFunction<Event, Discount, String, TimeWindow> {
    private static final int VIEW_THRESHOLD = 2; // Reduced from 3 to 2 for easier testing

    @Override
    public void open(Configuration parameters) throws Exception {
        log.info("ContinuousViewProcessor initialized with threshold: {}", VIEW_THRESHOLD);
    }

    @Override
    public void process(
            String key,
            Context context,
            Iterable<Event> elements,
            Collector<Discount> out) throws Exception {

        int count = 0;
        Event lastEvent = null;

        for (Event event : elements) {
            count++;
            lastEvent = event;
        }

        log.info("ContinuousViewProcessor: Processing window for key: {}, event count: {}", key, count);

        if (lastEvent != null && count >= VIEW_THRESHOLD) {
            Discount discount = new Discount(
                lastEvent.getProductId(),
                10.0,
                Duration.ofMinutes(30),
                count,
                "Continuous views discount"
            );
            log.info("ContinuousViewProcessor: Generated continuous view discount for key: {}, product: {}, count: {}",
                     key, lastEvent.getProductId(), count);
            out.collect(discount);
        } else if (lastEvent != null) {
            log.info("ContinuousViewProcessor: Not yet reached threshold of {} for key: {}, current count: {}",
                    VIEW_THRESHOLD, key, count);
        }
    }
}