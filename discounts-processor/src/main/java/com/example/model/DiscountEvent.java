package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DiscountEvent {
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("product_id")
    private String productId;
    
    private Discount discount;

    @Data
    public static class Discount {
        private double rate;
        
        @JsonProperty("by_view_time")
        private ViewTime byViewTime;
    }

    @Data
    public static class ViewTime {
        @JsonProperty("duration_in_seconds")
        private int durationInSeconds;
    }
}