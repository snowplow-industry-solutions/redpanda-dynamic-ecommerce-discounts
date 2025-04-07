package com.example;

import com.example.model.DiscountEvent;
import com.example.model.Event;
import org.apache.flink.api.common.functions.util.ListCollector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class DiscountsProcessorMockTest {

    @Test
    public void testContinuousViewDiscount() throws Exception {
        DiscountsProcessor.ContinuousViewProcessor processor = new DiscountsProcessor.ContinuousViewProcessor();
        processor.open(new Configuration());

        String userId = "test-user-1";
        String productId = "test-product-1";
        String webpageId = "test-page-1";

        List<Event> events = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < 9; i++) {
            events.add(new Event(
                productId,
                userId,
                "page_ping",
                webpageId,
                Instant.ofEpochMilli(baseTime + (i * 5000))
            ));
        }

        List<DiscountEvent> collectedDiscounts = new ArrayList<>();
        ListCollector<DiscountEvent> collector = new ListCollector<>(collectedDiscounts);

        TimeWindow window = new TimeWindow(baseTime, baseTime + 90000);
        DiscountsProcessor.ContinuousViewProcessor.Context context =
            mock(DiscountsProcessor.ContinuousViewProcessor.Context.class);

        processor.process(
            userId + "_" + webpageId,
            context,
            events,
            collector
        );

        assertEquals(1, collectedDiscounts.size(), "Should generate one discount");
        DiscountEvent discountEvent = collectedDiscounts.get(0);
        assertNotNull(discountEvent, "Discount event should not be null");
        assertEquals(userId, discountEvent.getUserId(), "User ID should match");
        assertEquals(productId, discountEvent.getProductId(), "Product ID should match");
        assertNotNull(discountEvent.getDiscount(), "Discount details should not be null");
        assertEquals(0.1, discountEvent.getDiscount().getRate(), "Discount rate should be 10%");
        assertNotNull(discountEvent.getDiscount().getByViewTime(), "View time should not be null");
        assertTrue(discountEvent.getDiscount().getByViewTime().getDurationInSeconds() >= 90,
            "Duration should be at least 90 seconds");
    }

    @Test
    public void testMostViewedProductDiscount() throws Exception {
        DiscountsProcessor.MostViewedProcessor processor = new DiscountsProcessor.MostViewedProcessor();

        processor.open(new Configuration());

        String productId = "test-product-2";
        String userId = "test-user-2";

        List<Event> events = new ArrayList<>();
        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < 5; i++) {
            events.add(new Event(
                productId,
                userId,
                "snowplow_ecommerce_action",
                "page-" + i,
                Instant.ofEpochMilli(baseTime + (i * 1000))
            ));
        }

        List<DiscountEvent> collectedDiscounts = new ArrayList<>();
        ListCollector<DiscountEvent> collector = new ListCollector<>(collectedDiscounts);

        TimeWindow window = new TimeWindow(baseTime, baseTime + 60000);
        DiscountsProcessor.MostViewedProcessor.Context context =
            mock(DiscountsProcessor.MostViewedProcessor.Context.class);

        processor.process(
            productId,
            context,
            events,
            collector
        );

        assertEquals(1, collectedDiscounts.size(), "Should generate one discount");
        DiscountEvent discountEvent = collectedDiscounts.get(0);
        assertNotNull(discountEvent, "Discount event should not be null");
        assertEquals(userId, discountEvent.getUserId(), "User ID should match");
        assertEquals(productId, discountEvent.getProductId(), "Product ID should match");
        assertNotNull(discountEvent.getDiscount(), "Discount details should not be null");
        assertEquals(0.1, discountEvent.getDiscount().getRate(), "Discount rate should be 10%");
    }
}
