package com.example.serialization;

import com.example.model.PagePingEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class EventTimestampExtractor implements TimestampExtractor {
    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        PagePingEvent event = (PagePingEvent) record.value();
        return event.getCollectorTimestamp().toEpochMilli();
    }
}