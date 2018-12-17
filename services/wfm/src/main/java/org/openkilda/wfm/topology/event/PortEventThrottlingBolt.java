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

package org.openkilda.wfm.topology.event;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.wfm.topology.event.service.PortEventThrottlingService;
import org.openkilda.wfm.topology.event.service.PortEventThrottlingService.PortInfoContainer;
import org.openkilda.wfm.topology.utils.AbstractTickRichBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The bolt implement similar antiflapping approach as for reroute.
 *
 * <p>Introduce a logic where we wait for a "Port UP" event after a "Port Down" for
 * "throttling_delay_seconds_min" seconds.
 * <br>If the last event in a series is "UP", then do not initiate "ISL_Down".
 *
 * @see PortEventThrottlingService for more details
 */
public class PortEventThrottlingBolt extends AbstractTickRichBolt {

    private static final Logger logger = LoggerFactory.getLogger(PortEventThrottlingBolt.class);

    private final int minDelay;
    private final int warmUpDelay;
    private final int coolDownDelay;
    private transient PortEventThrottlingService service;

    public static final String GROUPING_FIELD_NAME = "switch_port";

    public PortEventThrottlingBolt(int minDelay, int warmUpDelay, int coolDownDelay) {
        this.minDelay = minDelay;
        this.warmUpDelay = warmUpDelay;
        this.coolDownDelay = coolDownDelay;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
        service = new PortEventThrottlingService(minDelay, warmUpDelay, coolDownDelay);
        super.prepare(conf, context, collector);
    }

    @Override
    protected void doTick(Tuple tuple) {
        for (PortInfoContainer portInfoContainer: service.getPortInfos()) {
            sendTuple(portInfoContainer.portInfoData, portInfoContainer.correlationId, tuple);
        }
        outputCollector.ack(tuple);
    }

    @Override
    protected void doWork(Tuple tuple) {
        PortInfoData data = (PortInfoData) tuple.getValueByField(PAYLOAD);
        String correlationId = (String) tuple.getValueByField(CORRELATION_ID);
        if (service.processEvent(data, correlationId)) {
            sendTuple(data, correlationId, tuple);
        }
        outputCollector.ack(tuple);
    }

    private void sendTuple(PortInfoData data, String correlationId, Tuple tuple) {
        InfoMessage infoMessage = new InfoMessage(data, System.currentTimeMillis(), correlationId);
        try {
            String json = MAPPER.writeValueAsString(infoMessage);
            outputCollector.emit(tuple, new Values(json));
        } catch (JsonProcessingException e) {
            logger.error("Can't serialize info message", e);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(PAYLOAD));
    }
}
