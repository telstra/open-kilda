/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.bolts;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.service.RerouteService;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;


@Slf4j
public class RerouteBolt extends AbstractBolt implements SendRerouteRequestCarrier {

    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String THROTTLING_DATA_FIELD = "throttling-data";

    private PersistenceManager persistenceManager;
    private transient RerouteService rerouteService;

    private Tuple currentTuple;

    public RerouteBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.rerouteService = new RerouteService(persistenceManager, this);
        super.prepare(stormConf, context, collector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        currentTuple = tuple;
        CommandMessage message = pullValue(tuple, MessageTranslator.FIELD_ID_PAYLOAD, CommandMessage.class);
        CommandData commandData = message.getData();

        if (commandData instanceof RerouteAffectedFlows) {
            RerouteAffectedFlows rerouteAffectedFlows = (RerouteAffectedFlows) commandData;
            PathNode pathNode = rerouteAffectedFlows.getPathNode();
            rerouteService.processRerouteOnIslDown(pathNode.getSwitchId(), pathNode.getPortNo(),
                    rerouteAffectedFlows.getReason(), message.getCorrelationId());
        } else if (commandData instanceof RerouteInactiveFlows) {
            RerouteInactiveFlows rerouteInactiveFlows = (RerouteInactiveFlows) commandData;
            PathNode pathNode = rerouteInactiveFlows.getPathNode();
            rerouteService.processRerouteOnIslUp(pathNode.getSwitchId(), pathNode.getPortNo(),
                    rerouteInactiveFlows.getReason(), message.getCorrelationId());
        } else {
            log.warn("Skip undefined message type {}", message);
        }

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declare(new Fields(FLOW_ID_FIELD, THROTTLING_DATA_FIELD));
    }

    @Override
    public void sendRerouteRequest(String flowId, FlowThrottlingData payload, String reason) {
        getOutput().emit(currentTuple, new Values(flowId,payload));
        log.warn("Flow {} reroute command message sent with correlationId {}, reason \"{}\"",
                flowId, payload.getCorrelationId(), reason);
    }
}
