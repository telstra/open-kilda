package org.openkilda;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;

public class StormToKafkaTranslator extends FieldNameBasedTupleToKafkaMapper<String, String> {

    public static final String BOLT_KEY = "key";
    public static final String BOLT_MESSAGE = "value";

    public StormToKafkaTranslator() {
        super(BOLT_KEY, BOLT_MESSAGE);
    }
}