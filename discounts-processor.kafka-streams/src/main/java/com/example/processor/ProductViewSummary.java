package com.example.processor;

import com.example.model.PagePingEvent;
import com.example.model.ProductViewEvent;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductViewSummary {
  private final List<PagePingEvent> events;
  private final long pingCount;
  private final Optional<ProductViewEvent> productView;
}
