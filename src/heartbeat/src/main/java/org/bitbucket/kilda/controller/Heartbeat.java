package org.bitbucket.kilda.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
public class Heartbeat {

	private static final Logger logger = LoggerFactory.getLogger(Heartbeat.class);
	private final String topicName;
	private final String url;
	private final Properties props;
	private final Instant startTime;

	public Heartbeat(Properties props) {
		this.props = props;
		url = props.getOrDefault("kafka.host", "localhost") + ":" + props.getOrDefault("kafka.port", "9092");
		topicName = (String) props.getOrDefault("ops.heartbeat.topic", "kilda.heartbeat");
		startTime = Instant.now();
	}

	public Producer createProducer() {
		return this.new Producer();
	}

	public Consumer createConsumer() {
		return this.new Consumer();
	}

	public class Consumer implements Runnable {

		@Override
		public void run() {
			Properties kprops = new Properties();
			String sleepTimeStr = (String) props.getOrDefault("ops.heartbeat.listener.sleep", "5000");
			long sleepTime = StringUtils.isNumeric(sleepTimeStr) ? Long.decode(sleepTimeStr) : 5000L;

			kprops.put("bootstrap.servers", url);
			kprops.put("group.id", props.getOrDefault("ops.heartbeat.listener.group.id", "kilda.heartbeat.listener"));
			// NB: only "true" is valid; no code to handle "false" yet.
			kprops.put("enable.auto.commit", "true");
			kprops.put("auto.commit.interval.ms", props.getOrDefault("ops.heartbeat.listener.commit.interval", "1000"));
			kprops.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
			kprops.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
			KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kprops);
			consumer.subscribe(Arrays.asList(topicName));

			Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("heartbeat.consumer").build())
					.execute(new Runnable() {
						@Override
						public void run() {
							try {
								while (true) {
									logger.trace("==> Poll for records");
									ConsumerRecords<String, String> records = consumer.poll(1000);
									logger.trace("==> Number of records: " + records.count());
									// NB: if you want the beginning ..
									// consumer.seekToBeginning(records.partitions());
									for (ConsumerRecord<String, String> record : records)
										logger.debug("==> ==> offset = {}, key = {}, value = {}", record.offset(),
												record.key(), record.value());
									Thread.sleep(sleepTime);
								}
							} catch (InterruptedException e) {
								logger.info("Heartbeat Consumer Interrupted");
							} finally {
								consumer.close();
							}
						}
					});
		}

	}

	public class Producer implements Runnable {
		@Override
		public void run() {
			/*
			 * NB: This code assumes you have kafka available at the url
			 * address. This one is available through the services in this
			 * project - ie "docker-compose up zookeeper kafka" ** You'll have
			 * to put that into /etc/hosts if running this code from CLI.
			 */
			Properties kprops = new Properties();
			String sleepTimeStr = (String) props.getOrDefault("ops.heartbeat.sleep", "5000");
			long sleepTime = StringUtils.isNumeric(sleepTimeStr) ? Long.decode(sleepTimeStr) : 5000L;

			kprops.put("bootstrap.servers", url);
			kprops.put("acks", props.getOrDefault("ops.heartbeat.acks", "all"));
			kprops.put("retries", props.getOrDefault("ops.heartbeat.retries", "3"));
			kprops.put("batch.size", props.getOrDefault("ops.heartbeat.batch.size", "10000"));
			kprops.put("linger.ms", props.getOrDefault("ops.heartbeat.linger.ms", "1"));
			kprops.put("buffer.memory", props.getOrDefault("ops.heartbeat.buffer.memory", "10000000"));
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

	public static void main(String[] args) throws Exception {
		ImmutableMap<String, String> defaults = ImmutableMap.of("kafka.host", "kafka.pendev", "ops.heartbeat.sleep",
				"500", "ops.heartbeat.listener.commit.interval", "500", "ops.heartbeat.listener.sleep", "1500");
		Properties props = new Properties();
		props.putAll(defaults);
		Heartbeat hb = new Heartbeat(props);
		hb.createProducer().run();
		hb.createConsumer().run();
	}
}
