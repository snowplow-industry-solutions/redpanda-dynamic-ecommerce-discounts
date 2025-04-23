package com.example.processor;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Record;

@Slf4j
@AllArgsConstructor
public class ContinuousViewProcessorHelper {
  private final ConfigurationManager config;

  public boolean shouldProcessEvent(
      Long currentTimestamp, Long lastDiscountTime, String windowKey) {
    if (lastDiscountTime == null) {
      return true;
    }

    if (lastDiscountTime > currentTimestamp) {
      log.error(
          "Detected event with timestamp ({}) before last processed discount ({}). "
              + "This could indicate data replay or out-of-order events. Skipping processing. Key: {}",
          currentTimestamp,
          lastDiscountTime,
          windowKey);
      return false;
    }

    long cooldownPeriod = config.getWindowDurationMs();
    if (currentTimestamp < lastDiscountTime + cooldownPeriod) {
      log.debug(
          "Not enough time has passed since last discount. Key={}, lastDiscount={}, current={}",
          windowKey,
          lastDiscountTime,
          currentTimestamp);
      return false;
    }

    return true;
  }

  public boolean isInSameWindow(long lastDiscountTime, long currentTime) {
    if (lastDiscountTime > currentTime) {
      log.warn(
          "Invalid lastDiscountTime (in future): lastDiscount={}, current={}",
          lastDiscountTime,
          currentTime);
      return false;
    }
    return currentTime < lastDiscountTime + config.getWindowDurationMs();
  }

  public String createWindowKey(String userId, String webpageId) {
    return userId + ":" + webpageId;
  }

  public Record<String, DiscountEvent> processEventsAndCreateDiscount(
      String key,
      long numberOfPings,
      long currentTimestamp,
      Consumer<Record<String, DiscountEvent>> consumer) {

    String[] parts = key.split(":", 2);
    String userId = parts[0];
    String webpageId = parts[1];

    long durationInSeconds = config.calculateTotalViewingSeconds(numberOfPings);

    DiscountEvent discount =
        DiscountEvent.createContinuousViewDiscount(
            userId, webpageId, durationInSeconds, config.getDiscountRate(), currentTimestamp);

    log.info(
        "Generated continuous view discount: user={}, webpage={}, duration={}s, rate={}, timestamp={}",
        userId,
        webpageId,
        durationInSeconds,
        config.getDiscountRate(),
        currentTimestamp);

    var discountRecord = new Record<>(userId, discount, currentTimestamp);
    if (discountRecord != null) {
      consumer.accept(discountRecord);
    }
    return discountRecord;
  }
}
