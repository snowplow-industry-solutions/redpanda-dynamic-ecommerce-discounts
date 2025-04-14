package com.example;

import com.example.config.ConfigurationManager;
import com.example.model.DiscountEvent;
import com.example.model.PagePingEvent;
import com.example.processor.ContinuousViewProcessor;
import com.example.serialization.DiscountEventSerializationSchema;
import com.example.serialization.EventDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;

public class  DiscountsProcessor {
    private static final Logger log = LoggerFactory.getLogger(DiscountsProcessor.class);

    public static void main(String[] args) throws Exception {
        ConfigurationManager config = ConfigurationManager.getInstance();
        
        log.info("Configuring Discounts Processor");

        if (!config.isProcessorEnabled()) {
            log.error("Processor is not enabled");
            System.exit(1);
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        env.getConfig().setAutoWatermarkInterval(1000);
        
        env.setParallelism(config.getParallelism());
        log.debug("Set parallelism to: {}", config.getParallelism());

        Long checkpointInterval = config.getCheckpointInterval();
        if (checkpointInterval != null) {
            env.enableCheckpointing(checkpointInterval);
            log.debug("Enabled checkpointing with interval: {}ms", checkpointInterval);
        }

        Properties kafkaProperties = new Properties();
        kafkaProperties.setProperty("auto.offset.reset", "earliest");
        kafkaProperties.setProperty("enable.auto.commit", "false");
        kafkaProperties.setProperty("isolation.level", "read_committed");

        KafkaSource<PagePingEvent> source = KafkaSource.<PagePingEvent>builder()
            .setBootstrapServers(config.getBootstrapServers())
            .setTopics(config.getInputTopic())
            .setGroupId(config.getGroupId())
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new EventDeserializationSchema())
            .setProperties(kafkaProperties)
            .build();

        DataStream<PagePingEvent> pagePings = env
            .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
            .uid("kafka-source");

        // Configurar o timestamp assigner e watermark strategy
        WatermarkStrategy<PagePingEvent> watermarkStrategy = WatermarkStrategy
            .<PagePingEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
            .withTimestampAssigner((event, timestamp) -> event.getCollectorTimestamp().toEpochMilli());

        SingleOutputStreamOperator<DiscountEvent> continuousViewDiscounts = pagePings
            .assignTimestampsAndWatermarks(watermarkStrategy)
            .keyBy(event -> {
                log.debug("Keying by user_id: {}", event.getUserId());
                return event.getUserId();
            })
            .window(TumblingEventTimeWindows.of(config.getWindowDuration()))
            .trigger(EventTimeTrigger.create())
            .allowedLateness(Duration.ofMinutes(5))
            .process(new ContinuousViewProcessor())
            .name("continuous-view-processor")
            .uid("continuous-view-processor");

        DataStream<DiscountEvent> allDiscounts = continuousViewDiscounts
            .map(event -> {
                log.info("Generated discount event: user={}, product={}", 
                    event.getUserId(), event.getProductId());
                return event;
            })
            .name("discount-events-logger")
            .uid("discount-events-logger");

        log.info("Window processing configured with:");
        log.info("  - Window duration: {}s", config.getWindowDuration().getSeconds());
        log.info("  - Watermark interval: {}ms", env.getConfig().getAutoWatermarkInterval());
        log.info("  - Minimum pings required: {}", config.getMinPings());
        log.info("  - Minimum duration: {}s", config.getCalculatedMinDuration().getSeconds());

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

        log.info("Starting Discounts Processor");
        env.execute("Discounts Processor");
    }
}
