package com.example.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString(includeFieldNames = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagePingEvent {
  @JsonProperty("collector_tstamp")
  private Instant collectorTimestamp;

  @JsonProperty("event_name")
  private String eventName;

  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("webpage_id")
  private String webpageId;
}
