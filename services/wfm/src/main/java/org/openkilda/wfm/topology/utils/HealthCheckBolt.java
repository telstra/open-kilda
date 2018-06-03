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

package org.openkilda.wfm.topology.utils;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.wfm.topology.AbstractTopology.fieldMessage;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.HealthCheckInfoData;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class HealthCheckBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckBolt.class);

    private final HealthCheckInfoData healthCheck;
    private final String healthCheckTopic;

    private OutputCollector collector;

    public HealthCheckBolt(String service, String healthCheckTopic) {
        healthCheck = new HealthCheckInfoData(service, Utils.HEALTH_CHECK_OPERATIONAL_STATUS);

        this.healthCheckTopic = healthCheckTopic;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        String request = input.getString(0);
        try {
            Message message = MAPPER.readValue(request, Message.class);
            if (message instanceof CommandMessage) {
                Values values = new Values(Utils.MAPPER.writeValueAsString(new InfoMessage(healthCheck,
                        System.currentTimeMillis(), message.getCorrelationId(), Destination.NORTHBOUND)));
                collector.emit(healthCheckTopic, input, values);
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message: ", request, exception);
        } finally {
            collector.ack(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(healthCheckTopic, fieldMessage);
    }
}
