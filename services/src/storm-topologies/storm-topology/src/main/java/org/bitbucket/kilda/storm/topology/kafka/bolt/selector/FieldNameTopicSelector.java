package org.bitbucket.kilda.storm.topology.kafka.bolt.selector;

import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses field name to select topic name from tuple .
 */
public class FieldNameTopicSelector implements KafkaTopicSelector {
    private static final long serialVersionUID = -3903708904533396833L;
    private static final Logger LOG = LoggerFactory.getLogger(FieldNameTopicSelector.class);

    private final String fieldName;
    private final String defaultTopicName;


    public FieldNameTopicSelector(String fieldName, String defaultTopicName) {
        this.fieldName = fieldName;
        this.defaultTopicName = defaultTopicName;
    }

    @Override
    public String getTopic(Tuple tuple) {
        if (tuple.contains(fieldName)) {
            return tuple.getStringByField(fieldName);
        } else {
            LOG.warn("Field {} Not Found. Returning default topic {}", fieldName, defaultTopicName);
            return defaultTopicName;
        }
    }
}