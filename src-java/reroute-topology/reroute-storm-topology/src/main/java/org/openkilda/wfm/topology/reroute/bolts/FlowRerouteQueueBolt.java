/* Copyright 2020 Telstra Open Source
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

import static org.openkilda.wfm.topology.reroute.bolts.RerouteBolt.STREAM_MANUAL_REROUTE_REQUEST_ID;
import static org.openkilda.wfm.topology.reroute.bolts.RerouteBolt.STREAM_REROUTE_REQUEST_ID;
import static org.openkilda.wfm.topology.reroute.bolts.RerouteBolt.STREAM_REROUTE_RESULT_ID;
import static org.openkilda.wfm.topology.reroute.bolts.TimeWindowBolt.STREAM_TIME_WINDOW_EVENT_ID;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.hubandspoke.CoordinatedBolt;
import org.openkilda.wfm.topology.reroute.RerouteTopology;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.service.IRerouteQueueCarrier;
import org.openkilda.wfm.topology.reroute.service.RerouteQueueService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowRerouteQueueBolt extends CoordinatedBolt implements IRerouteQueueCarrier {

    public static final String BOLT_ID = "reroute-queue-bolt";
    public static final String STREAM_FLOWHS_ID = "flowhs";
    public static final String STREAM_NORTHBOUND_ID = "northbound-stream";

    private final int defaultFlowPriority;
    private final int maxRetry;
    private PersistenceManager persistenceManager;
    private transient RerouteQueueService rerouteQueueService;

    public FlowRerouteQueueBolt(PersistenceManager persistenceManager,
                                int defaultFlowPriority, int maxRetry, int rerouteTimeout) {
        super(true, rerouteTimeout);
        this.persistenceManager = persistenceManager;
        this.defaultFlowPriority = defaultFlowPriority;
        this.maxRetry = maxRetry;
    }

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        String sourceComponent = tuple.getSourceComponent();
        if (sourceComponent.equals(TimeWindowBolt.BOLT_ID)) {
            rerouteQueueService.flushThrottling();
        } else if (sourceComponent.equals(RerouteBolt.BOLT_ID)) {
            handleRerouteBoltMessage(tuple);
        } else {
            unhandledInput(tuple);
        }
    }

    private void handleRerouteBoltMessage(Tuple tuple) throws PipelineException {
        String flowId = pullValue(tuple, RerouteBolt.FLOW_ID_FIELD, String.class);
        FlowThrottlingData throttlingData;
        switch (tuple.getSourceStreamId()) {
            case STREAM_REROUTE_RESULT_ID:
                RerouteResultInfoData rerouteResultInfoData =
                        pullValue(tuple, RerouteBolt.REROUTE_RESULT_FIELD, RerouteResultInfoData.class);
                rerouteQueueService.processRerouteResult(rerouteResultInfoData,
                        getCommandContext().getCorrelationId());
                break;
            case STREAM_REROUTE_REQUEST_ID:
                throttlingData = (FlowThrottlingData) tuple.getValueByField(RerouteBolt.THROTTLING_DATA_FIELD);
                rerouteQueueService.processAutomaticRequest(flowId, throttlingData);
                break;
            case STREAM_MANUAL_REROUTE_REQUEST_ID:
                throttlingData = (FlowThrottlingData) tuple.getValueByField(RerouteBolt.THROTTLING_DATA_FIELD);
                rerouteQueueService.processManualRequest(flowId, throttlingData);
                break;
            default:
                unhandledInput(tuple);
        }
    }

    @Override
    protected void onTimeout(String key, Tuple tuple) {
        rerouteQueueService.handleTimeout(key);
    }

    @Override
    protected void init() {
        rerouteQueueService = new RerouteQueueService(this, persistenceManager, defaultFlowPriority, maxRetry);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(TimeWindowBolt.STREAM_TIME_WINDOW_EVENT_ID, new Fields(FIELD_ID_CONTEXT));
        declarer.declareStream(STREAM_FLOWHS_ID, RerouteTopology.KAFKA_FIELDS);
        declarer.declareStream(STREAM_NORTHBOUND_ID, RerouteTopology.KAFKA_FIELDS);
    }

    @Override
    public void sendRerouteRequest(String correlationId, FlowRerouteRequest request) {
        log.info("Send reroute request {} with correlationId {}", request, correlationId);
        getOutput().emit(STREAM_FLOWHS_ID, getCurrentTuple(),
                new Values(correlationId, new CommandMessage(request, System.currentTimeMillis(), correlationId)));
        registerCallback(correlationId);
    }

    @Override
    public void emitFlowRerouteError(ErrorData errorData) {
        String correlationId = getCommandContext().getCorrelationId();
        getOutput().emit(STREAM_NORTHBOUND_ID, getCurrentTuple(), new Values(correlationId,
                new ErrorMessage(errorData, System.currentTimeMillis(), correlationId)));
    }

    @Override
    public void sendExtendTimeWindowEvent() {
        getOutput().emit(STREAM_TIME_WINDOW_EVENT_ID, getCurrentTuple(), new Values(getCommandContext()));
    }

    @Override
    public void cancelTimeout(String key) {
        cancelCallback(key);
    }
}
