package org.bitbucket.kilda.storm.topology.kafka.bolt.mapper;

import org.apache.storm.tuple.Tuple;

import java.io.Serializable;

/**
 * Interface defining a mapping from storm tuple to kafka key and message.
 * @param <K> type of key.
 * @param <V> type of value.
 */
public interface TupleToKafkaMapper<K,V> extends Serializable {
	
    K getKeyFromTuple(Tuple tuple);
    V getMessageFromTuple(Tuple tuple);
    
}
