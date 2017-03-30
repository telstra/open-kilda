package org.bitbucket.kilda.storm.topology.kafka.bolt.selector;

import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses field with a given index to select the topic name from a tuple .
 */
public class FieldIndexTopicSelector implements KafkaTopicSelector {
    private static final long serialVersionUID = -3830575380208166367L;

    private static final Logger LOG = LoggerFactory.getLogger(FieldIndexTopicSelector.class);

    private final int fieldIndex;
    private final String defaultTopicName;

    public FieldIndexTopicSelector(int fieldIndex, String defaultTopicName) {
        this.fieldIndex = fieldIndex;
        if (fieldIndex < 0) {
            throw new IllegalArgumentException("fieldIndex cannot be negative");
        }
        this.defaultTopicName = defaultTopicName;
    }

    @Override
    public String getTopic(Tuple tuple) {
        if (fieldIndex < tuple.size()) {
            return tuple.getString(fieldIndex);
        } else {
            LOG.warn("Field index {} is out of bounds. Using default topic {}", fieldIndex, defaultTopicName);
            return defaultTopicName;
        }
    }
}