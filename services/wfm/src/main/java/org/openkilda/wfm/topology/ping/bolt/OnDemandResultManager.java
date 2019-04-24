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

import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowPingResponse.FlowPingResponseBuilder;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.Group;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class OnDemandResultManager extends ResultManager {
    public static final String BOLT_ID = ComponentId.ON_DEMAND_RESULT_MANAGER.toString();

    public static final Fields STREAM_FIELDS = new Fields(NorthboundEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    @Override
    protected void handle(Tuple input, PingContext pingContext) throws Exception {
        collectGroup(input, pingContext);
        super.handle(input, pingContext);
    }

    @Override
    protected void handleGroup(Tuple input) throws PipelineException {
        final Group group = pullPingGroup(input);
        FlowPingResponse response;
        try {
            response = collectResults(group);
            emit(input, response);
        } catch (IllegalArgumentException e) {
            for (FlowPingResponse errorResponse : produceErrors(group, e.getMessage())) {
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
                    throw new IllegalArgumentException(String.format(
                            "Unsupported %s.%s value",
                            entry.getDirection().getClass().getName(), entry.getDirection()));
            }
        }

        if (idSet.size() != 1) {
            throw new IllegalArgumentException(String.format(
                    "Expect exact one flow id in pings group response, got - \"%s\"", String.join("\", \"", idSet)));
        }
        builder.flowId(idSet.iterator().next());

        return builder.build();
    }

    private List<FlowPingResponse> produceErrors(Group group, String errorMessage) {
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

    private void emit(Tuple input, FlowPingResponse response) throws PipelineException {
        Values output = new Values(response, pullContext(input));
        getOutput().emit(input, output);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        declareGroupStream(outputManager);
        outputManager.declare(STREAM_FIELDS);
    }
}
