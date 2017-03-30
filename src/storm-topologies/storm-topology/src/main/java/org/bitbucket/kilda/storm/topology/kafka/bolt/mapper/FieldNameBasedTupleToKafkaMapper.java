package org.bitbucket.kilda.storm.topology.kafka.bolt.mapper;

import org.apache.storm.tuple.Tuple;

public class FieldNameBasedTupleToKafkaMapper<K,V> implements TupleToKafkaMapper<K, V> {
    private static final long serialVersionUID = -8794262989021702349L;
    public static final String BOLT_KEY = "key";
    public static final String BOLT_MESSAGE = "message";
    public String boltKeyField;
    public String boltMessageField;

    public FieldNameBasedTupleToKafkaMapper() {
        this(BOLT_KEY, BOLT_MESSAGE);
    }

    public FieldNameBasedTupleToKafkaMapper(String boltKeyField, String boltMessageField) {
        this.boltKeyField = boltKeyField;
        this.boltMessageField = boltMessageField;
    }

    @Override
    public K getKeyFromTuple(Tuple tuple) {
        //for backward compatibility, we return null when key is not present.
        return tuple.contains(boltKeyField) ? (K) tuple.getValueByField(boltKeyField) : null;
    }

    @Override
    public V getMessageFromTuple(Tuple tuple) {
        return (V) tuple.getValueByField(boltMessageField);
    }
}