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

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;

public class PingProducer extends Abstract {
    public static final String BOLT_ID = ComponentId.PING_PRODUCER.toString();

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws Exception {
        PingContext pingContext = pullPingContext(input);

        // TODO(surabujin): add one switch flow filter
        emit(input, produce(pingContext, FlowDirection.FORWARD));
        emit(input, produce(pingContext, FlowDirection.REVERSE));
    }

    private PingContext produce(PingContext pingContext, FlowDirection direction) {
        Ping ping = buildPing(pingContext, direction);
        return pingContext.toBuilder()
                .ping(ping)
                .direction(direction)
                .build();
    }

    private void emit(Tuple input, PingContext pingContext) throws PipelineException {
        CommandContext commandContext = pullContext(input);
        Values output = new Values(pingContext, commandContext);
        getOutput().emit(input, output);
    }

    private Ping buildPing(PingContext pingContext, FlowDirection direction) {
        Flow flow = pingContext.getFlow();
        FlowEndpoint ingress;
        FlowEndpoint egress;
        int islPort;
        if (FlowDirection.FORWARD == direction) {
            ingress = new FlowSourceAdapter(flow).getEndpoint();
            egress = new FlowDestAdapter(flow).getEndpoint();
            islPort = getIslPort(flow, flow.getForwardPath());
        } else if (FlowDirection.REVERSE == direction) {
            ingress = new FlowDestAdapter(flow).getEndpoint();
            egress = new FlowSourceAdapter(flow).getEndpoint();
            islPort = getIslPort(flow, flow.getReversePath());
        } else {
            throw new IllegalArgumentException(String.format(
                    "Unexpected %s value: %s", FlowDirection.class.getCanonicalName(), direction));
        }

        return new Ping(new NetworkEndpoint(ingress.getSwitchId(), ingress.getPortNumber()),
                        new NetworkEndpoint(egress.getSwitchId(), egress.getPortNumber()),
                        pingContext.getTransitEncapsulation(), islPort);
    }

    private int getIslPort(Flow flow, FlowPath flowPath) {
        List<PathSegment> segments = flowPath.getSegments();
        if (segments.isEmpty()) {
            throw new IllegalArgumentException(
                    format("Path segments not provided, flow_id: %s", flow.getFlowId()));
        }

        PathSegment ingressSegment = segments.get(0);
        if (!ingressSegment.getSrcSwitchId().equals(flowPath.getSrcSwitchId())) {
            throw new IllegalStateException(
                    format("FlowSegment was not found for ingress flow rule, flow_id: %s", flow.getFlowId()));
        }

        return  ingressSegment.getSrcPort();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
