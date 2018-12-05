package org.openkilda.hubandspoke;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.tuple.Fields;

public class StormToKafkaTranslator extends FieldNameBasedTupleToKafkaMapper<String, String> {

    public static final String BOLT_KEY = "key";
    public static final String BOLT_MESSAGE = "value";
    public static final Fields FIELDS = new Fields(StormToKafkaTranslator.BOLT_KEY,
            StormToKafkaTranslator.BOLT_MESSAGE);

    public StormToKafkaTranslator() {
        super(BOLT_KEY, BOLT_MESSAGE);
    }
}