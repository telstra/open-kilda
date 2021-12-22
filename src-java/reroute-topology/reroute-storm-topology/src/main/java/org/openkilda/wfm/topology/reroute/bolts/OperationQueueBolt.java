/* Copyright 2021 Telstra Open Source
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

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_KEY;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.info.reroute.PathSwapResult;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.CoordinatedBolt;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.reroute.service.OperationQueueService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class OperationQueueBolt extends CoordinatedBolt implements OperationQueueCarrier {
    public static final String BOLT_ID = "operation-queue";

    public static final String FLOW_ID_FIELD = RerouteBolt.FLOW_ID_FIELD;

    public static final String REROUTE_QUEUE_STREAM = "reroute-queue-stream";
    public static final Fields REROUTE_QUEUE_FIELDS = new Fields(FLOW_ID_FIELD, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public static final Fields FLOW_HS_FIELDS = new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD);

    private LifecycleEvent deferredShutdownEvent;

    private transient OperationQueueService service;

    public OperationQueueBolt(int defaultTimeout, String lifeCycleEventSourceComponent) {
        super(true, defaultTimeout, lifeCycleEventSourceComponent);
    }

    @Override
    protected void init() {
        this.service = new OperationQueueService(this);
    }

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        CommandContext context = pullContext(tuple);
        MessageData data = pullValue(tuple, FIELD_ID_PAYLOAD, MessageData.class);
        if (data instanceof FlowPathSwapRequest) {
            FlowPathSwapRequest flowPathSwapRequest = (FlowPathSwapRequest) data;
            service.addFirst(flowPathSwapRequest.getFlowId(), context.getCorrelationId(), flowPathSwapRequest);
        } else if (data instanceof FlowRerouteRequest) {
            FlowRerouteRequest flowRerouteRequest = (FlowRerouteRequest) data;
            service.addLast(flowRerouteRequest.getFlowId(), context.getCorrelationId(), flowRerouteRequest);
        } else if (data instanceof YFlowRerouteRequest) {
            YFlowRerouteRequest yFlowRerouteRequest = (YFlowRerouteRequest) data;
            service.addLast(yFlowRerouteRequest.getYFlowId(), context.getCorrelationId(), yFlowRerouteRequest);
        } else if (data instanceof RerouteResultInfoData) {
            RerouteResultInfoData rerouteResultInfoData = (RerouteResultInfoData) data;
            service.operationCompleted(rerouteResultInfoData.getFlowId(), rerouteResultInfoData);
            emitRerouteResponse(rerouteResultInfoData);
        } else if (data instanceof PathSwapResult) {
            PathSwapResult pathSwapResult = (PathSwapResult) data;
            service.operationCompleted(pathSwapResult.getFlowId(), pathSwapResult);
        } else {
            unhandledInput(tuple);
        }
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        if (service.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        service.activate();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer output) {
        super.declareOutputFields(output);
        output.declare(FLOW_HS_FIELDS);
        output.declareStream(REROUTE_QUEUE_STREAM, REROUTE_QUEUE_FIELDS);
        output.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }

    @Override
    protected void onTimeout(String key, Tuple tuple) {
        service.handleTimeout(key);
    }

    @Override
    public void emitRequest(String correlationId, CommandData commandData) {
        emit(getCurrentTuple(),
                new Values(correlationId, new CommandMessage(commandData, System.currentTimeMillis(), correlationId)));
        registerCallback(correlationId);
    }

    @Override
    public void sendInactive() {
        getOutput().emit(ZkStreams.ZK.toString(), new Values(deferredShutdownEvent, getCommandContext()));
        deferredShutdownEvent = null;

    }

    public void emitRerouteResponse(RerouteResultInfoData data) {
        emitWithContext(REROUTE_QUEUE_STREAM, getCurrentTuple(), new Values(data.getFlowId(), data));
    }
}
