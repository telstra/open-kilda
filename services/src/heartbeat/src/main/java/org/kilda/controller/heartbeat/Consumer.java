/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.bitbucket.kilda.controller.heartbeat;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bitbucket.kilda.controller.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

@Named
public class Consumer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(Consumer.class);
	
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
	
	@Inject
	@Named("ops.heartbeat.listener.group.id")
	private String groupId;
	
	@Inject
	@Named("ops.heartbeat.listener.commit.interval")
	private Integer commitInterval;
	
	@Override
	public void run() {
		Properties kprops = new Properties();

		kprops.put("bootstrap.servers", kafka.url());
		kprops.put("group.id", groupId);
		// NB: only "true" is valid; no code to handle "false" yet.
		kprops.put("enable.auto.commit", "true");
		kprops.put("auto.commit.interval.ms", commitInterval);
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
