package org.bitbucket.kilda.storm.topology.kafka.bolt.selector;

import org.apache.storm.tuple.Tuple;

import java.io.Serializable;

public interface KafkaTopicSelector extends Serializable {
    String getTopic(Tuple tuple);
}
