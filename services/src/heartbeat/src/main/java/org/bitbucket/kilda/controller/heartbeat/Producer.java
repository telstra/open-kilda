package org.bitbucket.kilda.controller.heartbeat;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bitbucket.kilda.controller.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Named
public class Producer implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(Producer.class);
	
	private final Instant startTime;
	
	@Inject
    private KafkaConfig kafka;

	@Inject
	@Named("ops.heartbeat.topic")
	private String topicName;
	
	@Inject
	@Named("ops.heartbeat.listener.sleep")
	private Long sleepTime;

	@Inject
	@Named("ops.heartbeat.acks")
	private String acks;

	@Inject
	@Named("ops.heartbeat.retries")
	private Integer retries;

	@Inject
	@Named("ops.heartbeat.batch.size")
	private Integer batchSize;
	
	@Inject
	@Named("ops.heartbeat.linger.ms")
	private Long linger;
	
	@Inject
	@Named("ops.heartbeat.buffer.memory.ms")
	private Long memory;
	
	@Inject
	@Named("ops.heartbeat.sleep")
	private Long producerSleepTime;
	
	public Producer() {
		startTime = Instant.now();
	}
	
	@Override
	public void run() {
		/*
		 * NB: This code assumes you have kafka available at the url
		 * address. This one is available through the services in this
		 * project - ie "docker-compose up zookeeper kafka" ** You'll have
		 * to put that into /etc/hosts if running this code from CLI.
		 */
		Properties kprops = new Properties();
		kprops.put("bootstrap.servers", kafka.url());
		kprops.put("acks", acks);
		kprops.put("retries", retries);	
		kprops.put("batch.size", batchSize);
		kprops.put("linger.ms", linger);
		kprops.put("buffer.memory", memory);
		kprops.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		kprops.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

		// TODO: use ScheduledExecutorService to scheduled fixed runnable
		// https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html
		// TODO: add shutdown hook, to be polite, regarding producer.close
		Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("heartbeat.producer").build())
				.execute(new Runnable() {
					@Override
					public void run() {
						KafkaProducer<String, String> producer = new KafkaProducer<>(kprops);
						try {
							while (true) {
								logger.trace("==> sending record");
								String now = Instant.now().toString();
								producer.send(new ProducerRecord<>(topicName, now, startTime.toString()));
								logger.debug("Sent record:  now = {}, start = {}", now, startTime);
								Thread.sleep(sleepTime);
							}
						} catch (InterruptedException e) {
							logger.info("Heartbeat Producer Interrupted");
						} finally {
							producer.close();
						}
					}
				});
	}
}