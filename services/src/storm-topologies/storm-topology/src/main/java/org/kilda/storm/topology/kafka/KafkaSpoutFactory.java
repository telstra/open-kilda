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

package org.bitbucket.kilda.storm.topology.kafka;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.kafka.spout.KafkaSpoutStream;
import org.apache.storm.kafka.spout.KafkaSpoutStreams;
import org.apache.storm.kafka.spout.KafkaSpoutStreamsNamedTopics;
import org.apache.storm.kafka.spout.KafkaSpoutTupleBuilder;
import org.apache.storm.kafka.spout.KafkaSpoutTuplesBuilder;
import org.apache.storm.kafka.spout.KafkaSpoutTuplesBuilderNamedTopics;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

@Named
public class KafkaSpoutFactory {

	private static final Logger logger = LoggerFactory.getLogger(KafkaSpoutFactory.class);
	
	private final String host;

	private final Integer port;

	private final String groupId;

	private final String keyDeserializer;

	private final String valueDeserializer;
	
	private final boolean enableAutoCommit;
	
	@Inject
	public KafkaSpoutFactory(@Named("kafka.host") String host, @Named("kafka.port") Integer port, @Named("kafka.spout.groupId") String groupId,
			@Named("kafka.spout.keyDeserializer") String keyDeserializer, @Named("kafka.spout.valueDeserializer") String valueDeserializer,
			@Named("kafka.spout.enableAutoCommit") boolean enableAutoCommit) {
		this.host = host;
		this.port = port;
		this.groupId = groupId;
		this.keyDeserializer = keyDeserializer;
		this.valueDeserializer = valueDeserializer;
		this.enableAutoCommit = enableAutoCommit;
	}

	public KafkaSpout<String, String> create(String topic, Class klass) {
		// KafkaSpout API seems to be undergoing quite a few changes.
		// Hopefully we can take advantage of the improvements in future versions soon!
		KafkaSpoutConfig<String, String> kafkaConfig = builder(getBootstrapServers(), topic, klass).build();
		return new KafkaSpout<String, String>(kafkaConfig);
	}

	public String getBootstrapServers() {
		return host + ":" + port;
	}

	public KafkaSpoutConfig.Builder<String, String> builder(String bootstrapServers, String topic, Class klass) {
		Map<String, Object> props = new HashMap<>();
		if (bootstrapServers == null || bootstrapServers.isEmpty()) {
			throw new IllegalArgumentException("bootstrap servers cannot be null");
		}
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		props.put(KafkaSpoutConfig.Consumer.GROUP_ID, groupId);
		props.put(KafkaSpoutConfig.Consumer.KEY_DESERIALIZER,
				keyDeserializer);
		props.put(KafkaSpoutConfig.Consumer.VALUE_DESERIALIZER,
				valueDeserializer);
		props.put(KafkaSpoutConfig.Consumer.ENABLE_AUTO_COMMIT, String.valueOf(enableAutoCommit));

		KafkaSpoutStreams streams = new KafkaSpoutStreamsNamedTopics.Builder(
				new KafkaSpoutStream(getFields(klass), topic)).build();

		KafkaSpoutTuplesBuilder<String, String> tuplesBuilder = new KafkaSpoutTuplesBuilderNamedTopics.Builder<String, String>(
				new TuplesBuilder(topic, klass)).build();

		return new KafkaSpoutConfig.Builder<>(props, streams, tuplesBuilder);
	}
	
	
	private Fields getFields(Class klass) {
		if (klass.isAnnotationPresent(OutputFields.class)) {
			OutputFields outputFields = (OutputFields) klass.getAnnotation(OutputFields.class);
			return new Fields(outputFields.value());
		} else {
			// TODO improve exception type and message
			throw new RuntimeException("can't get output fields for class " + klass);
		}
	}

	private static class TuplesBuilder extends KafkaSpoutTupleBuilder<String, String> {

		private final Class klass;
		private final ObjectMapper mapper;

		public TuplesBuilder(String topic, Class klass) {
			super(topic);
			this.klass = klass;
		    this.mapper = new ObjectMapper();
		}

		@Override
		public List<Object> buildTuple(ConsumerRecord<String, String> consumerRecord) {	
			// TODO - how to handle errors gracefully here?
			try {
				TupleProducer tupleProducer = (TupleProducer)mapper.readValue(consumerRecord.value(), klass);
				return tupleProducer.toTuple();
			} catch (JsonParseException e) {
				// bad JSON - need to handle this!
				logger.error("can't build tuple for consumer record " + consumerRecord, e);
				return new Values("not well-formed JSON!");
			} catch (UnrecognizedPropertyException e) {
				// bad JSON - need to handle this!
				logger.error("can't build tuple for consumer record " + consumerRecord, e);
				return new Values("unexpected fields in JSON!");
			} catch (JsonMappingException e) {
				// bad JSON - need to handle this!
				logger.error("can't build tuple for consumer record " + consumerRecord, e);
				return new Values("unexpected fields in JSON!");
			}  catch (IOException e) {
				logger.error("can't build tuple for consumer record " + consumerRecord, e);
				return null;
			}
		}

	}

}
