package org.bitbucket.kilda.storm.topology.kafka;

import org.apache.storm.tuple.Values;

public interface TupleProducer {
	
	Values toTuple();

}
