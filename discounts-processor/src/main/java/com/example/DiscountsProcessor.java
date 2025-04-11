package com.example;

import com.example.config.ConfigurationManager;
import com.example.model.PagePingEvent;
import com.example.model.DiscountEvent;
import com.example.processor.ContinuousViewProcessor;
import com.example.processor.MostViewedProcessor;
import com.example.serialization.DiscountEventSerializationSchema;
import com.example.serialization.EventDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;

public class DiscountsProcessor {
    private static final Logger log = LoggerFactory.getLogger(DiscountsProcessor.class);

    public static void main(String[] args) throws Exception {
        ConfigurationManager config = ConfigurationManager.getInstance();
        
        log.info("Starting Discounts Processor");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        env.setParallelism(config.getParallelism());
        log.info("Set parallelism to: {}", config.getParallelism());

        Long checkpointInterval = config.getCheckpointInterval();
        if (checkpointInterval != null) {
            env.enableCheckpointing(checkpointInterval);
            log.info("Enabled checkpointing with interval: {}ms", checkpointInterval);
        }

        KafkaSource<PagePingEvent> source = KafkaSource.<PagePingEvent>builder()
            .setBootstrapServers(config.getBootstrapServers())
            .setTopics(config.getInputTopic())
            .setGroupId(config.getGroupId())
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new EventDeserializationSchema())
            .setProperties(new Properties() {{
                put("partition.discovery.interval.ms", "30000");
                put("client.id.prefix", "discounts-processor");
            }})
            .build();

        DataStream<PagePingEvent> events = env
            .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
            .map(event -> {
                log.info("Received event: event_name={}, user_id={}, webpage_id={}",
                    event.getEventName(), event.getUserId(), event.getWebpageId());
                return event;
            });

        DataStream<PagePingEvent> pagePings = events
            .filter(event -> "page_ping".equals(event.getEventName()));

        DataStream<DiscountEvent> continuousViewDiscounts = pagePings
            .keyBy(event -> event.getUserId() + "_" + event.getWebpageId())
            .window(TumblingEventTimeWindows.of(config.getContinuousViewWindowDuration()))
            .process(new ContinuousViewProcessor());

        DataStream<DiscountEvent> mostViewedDiscounts = pagePings
            .keyBy(PagePingEvent::getUserId)
            .window(TumblingEventTimeWindows.of(java.time.Duration.ofMinutes(5)))
            .process(new MostViewedProcessor());

        DataStream<DiscountEvent> allDiscounts = continuousViewDiscounts
            .union(mostViewedDiscounts);

        KafkaSink<DiscountEvent> sink = KafkaSink.<DiscountEvent>builder()
            .setBootstrapServers(config.getBootstrapServers())
            .setRecordSerializer(KafkaRecordSerializationSchema.<DiscountEvent>builder()
                .setTopic(config.getOutputTopic())
                .setValueSerializationSchema(new DiscountEventSerializationSchema())
                .build())
            .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
            .setProperty("metrics.group.id", "discounts-processor-sink")
            .build();

        allDiscounts.sinkTo(sink);

        log.info("Starting Flink job execution");
        env.execute("Discounts Processor");
    }
}
