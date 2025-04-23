package com.example.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscountEvent {
  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("product_id")
  private String productId;

  private Discount discount;

  @JsonProperty("generated_at")
  @JsonFormat(
      shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      timezone = "UTC")
  private Instant generatedAt;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @ToString
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Discount {
    private double rate;

    @JsonProperty("by_view_time")
    private ViewTime byViewTime;

    @JsonProperty("by_number_of_views")
    private NumberOfViews byNumberOfViews;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @ToString
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ViewTime {
    @JsonProperty("duration_in_seconds")
    private long durationInSeconds;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @ToString
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class NumberOfViews {
    private int views;

    @JsonProperty("duration_in_seconds")
    private long durationInSeconds;
  }

  public static DiscountEvent createContinuousViewDiscount(
      String userId,
      String productId,
      long durationInSeconds,
      double discountRate,
      long timestamp) {
    DiscountEvent event = new DiscountEvent();
    event.setUserId(userId);
    event.setProductId(productId);
    event.setGeneratedAt(Instant.ofEpochMilli(timestamp));

    Discount discount = new Discount();
    discount.setRate(discountRate);
    discount.setByViewTime(new ViewTime(durationInSeconds));

    event.setDiscount(discount);
    return event;
  }

  public static DiscountEvent createMostViewedDiscount(
      String userId,
      String productId,
      int views,
      long durationInSeconds,
      double discountRate,
      long timestamp) {
    DiscountEvent event = new DiscountEvent();
    event.setUserId(userId);
    event.setProductId(productId);
    event.setGeneratedAt(Instant.ofEpochMilli(timestamp));

    Discount discount = new Discount();
    discount.setRate(discountRate);
    discount.setByNumberOfViews(new NumberOfViews(views, durationInSeconds));

    event.setDiscount(discount);
    return event;
  }
}
