package com.example;

import com.example.model.Discount;
import com.example.model.Event;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DiscountsProcessorTest {

  @Test
  void testContinuousViewDiscount() {
    Event event = new Event(
        "product1",
        "user1",
        "page_view",
        "page1",
        Instant.now());

    Discount discount = new Discount(
        event.getProductId(),
        10.0,
        Duration.ofMinutes(30),
        3,
        "Continuous views discount");

    assertNotNull(discount);
    assertEquals("product1", discount.getProductId());
    assertEquals(10.0, discount.getPercentage());
    assertNotNull(discount.getDuration());
    assertEquals(3, discount.getViews());
    assertEquals("Continuous views discount", discount.getReason());
  }

  @Test
  void testMostViewedDiscount() {
    Event event = new Event(
        "product1",
        "user1",
        "page_view",
        "page1",
        Instant.now());

    Discount discount = new Discount(
        event.getProductId(),
        10.0,
        2,
        "Most viewed product discount");

    assertNotNull(discount);
    assertEquals("product1", discount.getProductId());
    assertEquals(10.0, discount.getPercentage());
    assertEquals(2, discount.getViews());
    assertEquals("Most viewed product discount", discount.getReason());
  }
}
