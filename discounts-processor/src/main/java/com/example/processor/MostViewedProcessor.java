package com.example.processor;

import com.example.model.Discount;
import com.example.model.Event;
import lombok.extern.slf4j.Slf4j;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

@Slf4j
public class MostViewedProcessor extends ProcessWindowFunction<Event, Discount, String, TimeWindow> {
    private static final int VIEW_THRESHOLD = 2;

    @Override
    public void open(Configuration parameters) throws Exception {
        log.info("MostViewedProcessor initialized with threshold: {}", VIEW_THRESHOLD);
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

        log.info("MostViewedProcessor: Processing window for product: {}, event count: {}", key, count);

        if (lastEvent != null && count >= VIEW_THRESHOLD) {
            Discount discount = new Discount(
                key,
                10.0,
                count,
                "Most viewed product discount"
            );
            log.info("MostViewedProcessor: Generated most viewed discount for product: {}, count: {}", key, count);
            out.collect(discount);
        } else if (lastEvent != null) {
            log.info("MostViewedProcessor: Not yet reached threshold of {} for product: {}, current count: {}",
                    VIEW_THRESHOLD, key, count);
        }
    }
}
