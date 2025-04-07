package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Duration;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Discount {
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("percentage")
    private double percentage;
    
    @JsonProperty("duration")
    private Duration duration;
    
    @JsonProperty("views")
    private int views;
    
    @JsonProperty("reason")
    private String reason;

    public Discount(String productId, double percentage, Duration duration, String reason) {
        this(productId, percentage, duration, 0, reason);
    }

    public Discount(String productId, double percentage, int views, String reason) {
        this(productId, percentage, Duration.ofHours(1), views, reason);
    }
}
