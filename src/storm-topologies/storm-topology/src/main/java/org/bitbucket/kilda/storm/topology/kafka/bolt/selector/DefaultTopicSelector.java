package org.bitbucket.kilda.storm.topology.kafka.bolt.selector;

import org.apache.storm.tuple.Tuple;

public class DefaultTopicSelector implements KafkaTopicSelector {
    private static final long serialVersionUID = 4601118062437851265L;
    private final String topicName;

    public DefaultTopicSelector(final String topicName) {
        this.topicName = topicName;
    }

    @Override
    public String getTopic(Tuple tuple) {
        return topicName;
    }
}
