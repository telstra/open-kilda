package org.bitbucket.kilda.controller.heartbeat;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Heartbeat is a simple mechanism for monitoring health.
 *
 * It sends a record to a kafka topic every period (the period is configurable).
 *
 * REFERENCES - Producer:
 * https://kafka.apache.org/0101/javadoc/index.html?org/apache/kafka/clients/producer/KafkaProducer.html
 * - Consumer:
 * https://kafka.apache.org/0101/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html
 */
@Named
public class Heartbeat {
	
	@Inject
	private Consumer hbc;
	
	@Inject
	private Producer hbp;

	public void start() {
		hbp.run();
		hbc.run();
	}

}
