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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteAffectedInactiveFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.reroute.RerouteTopology;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.service.RerouteService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;

@Slf4j
public class RerouteBolt extends AbstractBolt implements MessageSender {

    public static final String FLOW_ID_FIELD = "flow-id";
    public static final String THROTTLING_DATA_FIELD = "throttling-data";
    public static final String REROUTE_RESULT_FIELD = "reroute-result";
    public static final String BOLT_ID = "reroute-bolt";
    public static final String STREAM_REROUTE_REQUEST_ID = "reroute-request-stream";
    public static final String STREAM_MANUAL_REROUTE_REQUEST_ID = "manual-reroute-request-stream";
    public static final String STREAM_REROUTE_RESULT_ID = "reroute-result-stream";
    public static final String STREAM_SWAP_ID = "swap-stream";

    private PersistenceManager persistenceManager;
    private transient RerouteService rerouteService;


    public RerouteBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.rerouteService = new RerouteService(persistenceManager);
        super.prepare(stormConf, context, collector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        Message message = pullValue(tuple, MessageKafkaTranslator.FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof CommandMessage) {
            handleCommandMessage((CommandMessage) message);
        } else if (message instanceof InfoMessage) {
            handleInfoMessage(message);
        } else {
            unhandledInput(tuple);
        }
    }

    private void handleCommandMessage(CommandMessage commandMessage) {
        CommandData commandData = commandMessage.getData();
        String correlationId = getCommandContext().getCorrelationId();
        if (commandData instanceof RerouteAffectedFlows) {
            rerouteService.rerouteAffectedFlows(this, correlationId, (RerouteAffectedFlows) commandData);
        } else if (commandData instanceof RerouteAffectedInactiveFlows) {
            rerouteService.rerouteInactiveAffectedFlows(this, correlationId,
                    ((RerouteAffectedInactiveFlows) commandData).getSwitchId());
        } else if (commandData instanceof RerouteInactiveFlows) {
            rerouteService.rerouteInactiveFlows(this, correlationId, (RerouteInactiveFlows) commandData);
        } else if (commandData instanceof FlowRerouteRequest) {
            rerouteService.processManualRerouteRequest(this, correlationId, (FlowRerouteRequest) commandData);
        } else {
            unhandledInput(getCurrentTuple());
        }
    }

    private void handleInfoMessage(Message message) {
        if (message instanceof InfoMessage) {
            InfoData infoData = ((InfoMessage) message).getData();
            if (infoData instanceof RerouteResultInfoData) {
                RerouteResultInfoData rerouteResultInfoData = (RerouteResultInfoData) infoData;
                emitWithContext(STREAM_REROUTE_RESULT_ID, getCurrentTuple(),
                        new Values(rerouteResultInfoData.getFlowId(), rerouteResultInfoData));
            } else {
                unhandledInput(getCurrentTuple());
            }
        } else {
            unhandledInput(getCurrentTuple());
        }
    }

    /**
     * Emit reroute command for consumer.
     * @param flowId flow id
     * @param flowThrottlingData flow throttling data
     */
    @Override
    public void emitRerouteCommand(String flowId, FlowThrottlingData flowThrottlingData) {
        String newCorrelationId = new CommandContext(flowThrottlingData.getCorrelationId())
                .fork(flowId).getCorrelationId();
        flowThrottlingData.setCorrelationId(newCorrelationId);
        emitWithContext(STREAM_REROUTE_REQUEST_ID, getCurrentTuple(), new Values(flowId, flowThrottlingData));

        log.warn("Flow {} reroute command message sent with correlationId {}, reason \"{}\"",
                flowId, flowThrottlingData.getCorrelationId(), flowThrottlingData.getReason());
    }

    /**
     * Emit manual reroute command for consumer.
     * @param flowId flow id
     * @param flowThrottlingData flow throttling data
     */
    @Override
    public void emitManualRerouteCommand(String flowId, FlowThrottlingData flowThrottlingData) {
        emitWithContext(STREAM_MANUAL_REROUTE_REQUEST_ID, getCurrentTuple(), new Values(flowId, flowThrottlingData));

        log.info("Manual reroute command message sent for flow {}", flowId);
    }

    /**
     * Emit swap command for consumer.
     *
     * @param correlationId correlation id to pass through
     * @param path affected paths
     * @param reason initial reason of reroute
     */
    @Override
    public void emitPathSwapCommand(String correlationId, FlowPath path, String reason) {
        FlowPathSwapRequest request = new FlowPathSwapRequest(path.getFlow().getFlowId(), path.getPathId());
        getOutput().emit(STREAM_SWAP_ID, getCurrentTuple(), new Values(correlationId,
                new CommandMessage(request, System.currentTimeMillis(), correlationId)));

        log.warn("Flow {} swap path command message sent with correlationId {}, reason \"{}\"",
                path.getFlow().getFlowId(), correlationId, reason);
    }



    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        output.declareStream(STREAM_REROUTE_REQUEST_ID,
                new Fields(FLOW_ID_FIELD, THROTTLING_DATA_FIELD, FIELD_ID_CONTEXT));
        output.declareStream(STREAM_MANUAL_REROUTE_REQUEST_ID,
                new Fields(FLOW_ID_FIELD, THROTTLING_DATA_FIELD, FIELD_ID_CONTEXT));
        output.declareStream(STREAM_REROUTE_RESULT_ID,
                new Fields(FLOW_ID_FIELD, REROUTE_RESULT_FIELD, FIELD_ID_CONTEXT));
        output.declareStream(STREAM_SWAP_ID, RerouteTopology.KAFKA_FIELDS);
    }
}
