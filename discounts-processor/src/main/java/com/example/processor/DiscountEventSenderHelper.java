package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscountEventSenderHelper {
  private final ConfigurationManager config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public DiscountEventSenderHelper() {
    this.config = ConfigurationManager.getInstance();
    this.objectMapper = new ObjectMapper();
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public void sendToSnowplow(DiscountEvent discountEvent) {
    try {
      String snowplowEvent = buildSnowplowEvent(discountEvent);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(config.getSnowplowCollectorUrl()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(snowplowEvent))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        log.info("Successfully sent event to Snowplow: {}", discountEvent);
      } else {
        log.error(
            "Failed to send event to Snowplow. Status: {}, Response: {}",
            response.statusCode(),
            response.body());
      }
    } catch (Exception e) {
      log.error("Error sending event to Snowplow: {}", discountEvent, e);
    }
  }

  private String buildSnowplowEvent(DiscountEvent discountEvent) {
    return """
                {
                    "schema": "%s",
                    "data": {
                        "user_id": "%s",
                        "product_id": "%s",
                        "discount": %s,
                        "generated_at": "%s"
                    }
                }
                """
        .formatted(
            config.getSnowplowSchemaUri(),
            discountEvent.getUserId(),
            discountEvent.getProductId(),
            buildDiscountJson(discountEvent.getDiscount()),
            discountEvent.getGeneratedAt());
  }

  private String buildDiscountJson(DiscountEvent.Discount discount) {
    StringBuilder json = new StringBuilder();
    json.append(
        """
                {
                    "rate": %s
                """
            .formatted(discount.getRate()));

    if (discount.getByViewTime() != null) {
      json.append(
          """
                    ,
                    "by_view_time": {
                        "duration_in_seconds": %d
                    }
                    """
              .formatted(discount.getByViewTime().getDurationInSeconds()));
    }

    if (discount.getByNumberOfViews() != null) {
      json.append(
          """
                    ,
                    "by_number_of_views": {
                        "views": %d,
                        "duration_in_seconds": %d
                    }
                    """
              .formatted(
                  discount.getByNumberOfViews().getViews(),
                  discount.getByNumberOfViews().getDurationInSeconds()));
    }

    json.append("\n}");
    return json.toString();
  }
}
