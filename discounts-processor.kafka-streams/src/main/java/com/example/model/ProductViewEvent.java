package com.example.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductViewEvent extends PagePingEvent {
  @JsonProperty("product_name")
  private String productName;

  @JsonProperty("product_price")
  private Double productPrice;

  @JsonProperty("product_id")
  private String productId;
}
