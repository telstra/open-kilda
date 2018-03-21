/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.wfm.protocol.BoltToBoltMessage.FIELD_ID_CORRELATION_ID;
import static org.openkilda.wfm.protocol.JsonMessage.FIELD_ID_JSON;
import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;

import org.apache.storm.state.State;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.IStatefulBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openkilda.messaging.Message;
import org.openkilda.wfm.topology.utils.CorrelationContext.CorrelationContextClosable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * A proxy for IRichBolt / IStatefulBolt which decorates processing of a tuple with the message correlation context.
 */
public final class BoltProxyWithCorrelationContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(BoltProxyWithCorrelationContext.class);

    public static IRichBolt proxyWithCorrelationContext(IRichBolt richBolt) {
        return new IRichBolt() {
            @Override
            public void execute(Tuple input) {
                String correlationId = extractCorrelationId(input);
                if (correlationId == null || correlationId.trim().isEmpty()) {
                    correlationId = UUID.randomUUID().toString();
                    LOGGER.debug("CorrelationId was not sent, generated one: {}", correlationId);
                }

                try (CorrelationContextClosable closable = CorrelationContext.create(correlationId)) {
                    richBolt.execute(input);
                }
            }

            @Override
            public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
                richBolt.prepare(stormConf, context, collector);
            }

            @Override
            public void cleanup() {
                richBolt.cleanup();
            }

            @Override
            public void declareOutputFields(OutputFieldsDeclarer declarer) {
                richBolt.declareOutputFields(declarer);
            }

            @Override
            public Map<String, Object> getComponentConfiguration() {
                return richBolt.getComponentConfiguration();
            }
        };
    }

    public static <T extends State> IStatefulBolt<T> proxyWithCorrelationContext(IStatefulBolt<T> statefulBolt) {
        return new IStatefulBolt<T>() {

            @Override
            public void execute(Tuple input) {
                String correlationId = extractCorrelationId(input);
                if (correlationId == null || correlationId.trim().isEmpty()) {
                    correlationId = UUID.randomUUID().toString();
                    LOGGER.debug("CorrelationId was not sent, generated one: {}", correlationId);
                }

                try (CorrelationContextClosable closable = CorrelationContext.create(correlationId)) {
                    statefulBolt.execute(input);
                }
            }

            @Override
            public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
                statefulBolt.prepare(stormConf, context, collector);
            }

            @Override
            public void cleanup() {
                statefulBolt.cleanup();
            }

            @Override
            public void initState(T state) {
                statefulBolt.initState(state);
            }

            @Override
            public void preCommit(long txid) {
                statefulBolt.preCommit(txid);
            }

            @Override
            public void prePrepare(long txid) {
                statefulBolt.prePrepare(txid);
            }

            @Override
            public void preRollback() {
                statefulBolt.preRollback();
            }

            @Override
            public void declareOutputFields(OutputFieldsDeclarer declarer) {
                statefulBolt.declareOutputFields(declarer);
            }

            @Override
            public Map<String, Object> getComponentConfiguration() {
                return statefulBolt.getComponentConfiguration();
            }
        };
    }

    private static String extractCorrelationId(Tuple input) {
        final Fields fields = input.getFields();
        if (fields.contains(FIELD_ID_CORRELATION_ID)) {
            String correlationId = input.getStringByField(FIELD_ID_CORRELATION_ID);
            if (correlationId != null && !correlationId.trim().isEmpty()) {
                return correlationId;
            }
        }

        if (fields.contains(MESSAGE_FIELD)) {
            Object messageObj = input.getValueByField(MESSAGE_FIELD);
            if (messageObj instanceof Message) {
                String correlationId = ((Message) messageObj).getCorrelationId();
                if (correlationId != null && !correlationId.trim().isEmpty()) {
                    return correlationId;
                }
            }
        }

        if (fields.contains(FIELD_ID_JSON)) {
            JSONParser jsonParser = new JSONParser();
            try {
                Object json = jsonParser.parse(input.getStringByField(FIELD_ID_JSON));
                if (json instanceof JSONObject) {
                    Object corrIdObj = ((JSONObject) json).get(CORRELATION_ID);
                    if (corrIdObj instanceof String) {
                        return (String) corrIdObj;
                    }
                }
            } catch (ParseException ex) {
                LOGGER.warn("Unable to parse message payload as a JSON object.", ex);
            }
        }

        return null;
    }

    private BoltProxyWithCorrelationContext() {
    }
}
