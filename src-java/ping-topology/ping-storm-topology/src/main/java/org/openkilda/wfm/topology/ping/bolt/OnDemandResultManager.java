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

package org.openkilda.wfm.topology.ping.bolt;

import static java.lang.String.format;
import static org.openkilda.messaging.model.FlowDirection.FORWARD;
import static org.openkilda.messaging.model.FlowDirection.REVERSE;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowPingResponse.FlowPingResponseBuilder;
import org.openkilda.messaging.info.flow.SubFlowPingPayload;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.messaging.info.flow.UniSubFlowPingPayload;
import org.openkilda.messaging.info.flow.YFlowPingResponse;
import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.Group;
import org.openkilda.wfm.topology.ping.model.Group.Type;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OnDemandResultManager extends ResultManager {
    public static final String BOLT_ID = ComponentId.ON_DEMAND_RESULT_MANAGER.toString();

    public static final Fields STREAM_FIELDS = new Fields(NorthboundEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    @Override
    protected void handle(Tuple input, PingContext pingContext) throws PipelineException {
        collectGroup(input, pingContext);
        super.handle(input, pingContext);
    }

    @Override
    protected void handleGroup(Tuple input) throws PipelineException {
        final Group group = pullPingGroup(input);
        if (group.getType() == Type.FLOW) {
            handleFlowResponse(input, group);
        } else if (group.getType() == Type.Y_FLOW) {
            handleYFlowResponse(input, group);
        } else {
            unhandledInput(input);
        }
    }

    private void handleFlowResponse(Tuple input, Group group) throws PipelineException {
        try {
            FlowPingResponse response = collectResults(group);
            emit(input, response);
        } catch (IllegalArgumentException e) {
            for (FlowPingResponse errorResponse : produceFlowErrors(group, e.getMessage())) {
                emit(input, errorResponse);
            }
        }
    }

    private FlowPingResponse collectResults(Group group) {
        FlowPingResponseBuilder builder = FlowPingResponse.builder();

        HashSet<String> idSet = new HashSet<>();
        for (PingContext entry : group.getRecords()) {
            idSet.add(entry.getFlowId());

            switch (entry.getDirection()) {
                case FORWARD:
                    builder.forward(makeResponse(entry));
                    break;
                case REVERSE:
                    builder.reverse(makeResponse(entry));
                    break;
                default:
                    throw new IllegalArgumentException(format("Unsupported %s.%s value",
                            entry.getDirection().getClass().getName(), entry.getDirection()));
            }
        }

        if (idSet.size() != 1) {
            throw new IllegalArgumentException(format(
                    "Expect exact one flow id in pings group response, got - \"%s\"", String.join("\", \"", idSet)));
        }
        builder.flowId(idSet.iterator().next());

        return builder.build();
    }

    private List<FlowPingResponse> produceFlowErrors(Group group, String errorMessage) {
        HashSet<String> seen = new HashSet<>();
        ArrayList<FlowPingResponse> results = new ArrayList<>();
        for (PingContext pingContext : group.getRecords()) {
            if (!seen.add(pingContext.getFlowId())) {
                continue;
            }

            log.info(
                    "Produce error response (group={}, flow={}): {}",
                    group.getId(), pingContext.getFlowId(), errorMessage);

            results.add(new FlowPingResponse(pingContext.getFlowId(), errorMessage));
        }

        return results;
    }

    private UniFlowPingResponse makeResponse(PingContext pingContext) {
        return new UniFlowPingResponse(pingContext.getPing(), pingContext.getMeters(), pingContext.getError());
    }

    private void handleYFlowResponse(Tuple input, Group group) throws PipelineException {
        try {
            YFlowPingResponse response = buildYFlowPingResponse(group);
            emit(input, response);
        } catch (IllegalArgumentException e) {
            String yFlowId = group.getRecords().stream().map(PingContext::getYFlowId).filter(Objects::nonNull)
                    .findFirst().orElse(null);
            YFlowPingResponse errorResponse = new YFlowPingResponse(yFlowId, e.getMessage(), null);
            emit(input, errorResponse);
        }
    }

    private YFlowPingResponse buildYFlowPingResponse(Group group) {
        Map<String, SubFlowPingPayload> subFlowMap = new HashMap<>();
        Set<String> yFlowIdSet = new HashSet<>();

        for (PingContext record : group.getRecords()) {
            if (record.getYFlowId() == null) {
                throw new IllegalArgumentException(format("Ping report %s has no yFlowId", record));
            }
            yFlowIdSet.add(record.getYFlowId());
            SubFlowPingPayload subFlow = subFlowMap.computeIfAbsent(record.getFlowId(),
                    mappingFunction -> new SubFlowPingPayload(record.getFlowId(), null, null));

            switch (record.getDirection()) {
                case FORWARD:
                    validateSubFlow(subFlow.getForward(), FORWARD, group, record, subFlow.getFlowId());
                    subFlow.setForward(makeSubFlowPayload(record));
                    break;
                case REVERSE:
                    validateSubFlow(subFlow.getReverse(), REVERSE, group, record, subFlow.getFlowId());
                    subFlow.setReverse(makeSubFlowPayload(record));
                    break;
                default:
                    throw new IllegalArgumentException(format("Unsupported %s.%s value",
                            record.getDirection().getClass().getName(), record.getDirection()));
            }
        }

        if (subFlowMap.size() * 2 != group.getRecords().size()) {
            throw new IllegalArgumentException(format(
                    "Expect %d unique Flow IDs in ping group responses, but got responses about %d Flows: %s",
                    group.getRecords().size() / 2, subFlowMap.size(), subFlowMap.keySet()));
        }

        if (yFlowIdSet.size() != 1) {
            throw new IllegalArgumentException(format(
                    "Expect exact one Y Flow id in pings group response, got - %s", yFlowIdSet));
        }

        String error = oneSwitchFlowExists(subFlowMap) ? "One sub_flow is one-switch flow" : null;

        return new YFlowPingResponse(yFlowIdSet.iterator().next(), error, new ArrayList<>(subFlowMap.values()));
    }

    private boolean oneSwitchFlowExists(Map<String, SubFlowPingPayload> subFlowMap) {
        //map size is odd means that one subflow was not included
        return subFlowMap.size() % 2 == 1;
    }

    private void validateSubFlow(UniSubFlowPingPayload existPayloadPayload, FlowDirection direction,
                                 Group group, PingContext pingContext, String flowId) {
        if (existPayloadPayload != null) {
            throw new IllegalArgumentException(
                    format("Ping Group %s has 2 ping reports for %s direction of Flow %s. "
                                    + "First report %s, Second report %s", group, direction, flowId,
                            existPayloadPayload, pingContext));
        }
    }

    private UniSubFlowPingPayload makeSubFlowPayload(PingContext pingContext) {
        long latency = 0;
        if (pingContext.getMeters() != null) {
            latency = pingContext.getMeters().getNetworkLatency();
        }
        return new UniSubFlowPingPayload(pingContext.getError(), latency);
    }

    private void emit(Tuple input, InfoData response) throws PipelineException {
        Values output = new Values(response, pullContext(input));
        getOutput().emit(input, output);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        declareGroupStream(outputManager);
        outputManager.declare(STREAM_FIELDS);
    }
}
