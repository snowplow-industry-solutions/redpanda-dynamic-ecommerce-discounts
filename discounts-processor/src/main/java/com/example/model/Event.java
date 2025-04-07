package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("event_name")
    private String eventName;
    
    @JsonProperty("webpage_id")
    private String webpageId;
    
    @JsonProperty("collector_tstamp")
    private Instant collectorTimestamp;
}
