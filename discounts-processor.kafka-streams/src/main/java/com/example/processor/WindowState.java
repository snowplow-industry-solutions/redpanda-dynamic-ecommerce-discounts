package com.example.processor;

import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class WindowState {
  private List<PagePingEvent> events = new ArrayList<>();
  private Long lastDiscountTimestamp;
  private boolean discountGenerated;
  private DiscountEvent generatedDiscount;
  private boolean hasRejectedEvents;

  public void markRejectedEvents() {
    this.hasRejectedEvents = true;
  }

  public boolean hasRejectedEvents() {
    return hasRejectedEvents;
  }

  public void addEvent(PagePingEvent event) {
    events.add(event);
  }
}
