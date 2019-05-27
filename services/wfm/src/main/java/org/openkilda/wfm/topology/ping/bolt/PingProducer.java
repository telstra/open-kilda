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

import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.Ping;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.GroupId;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PingProducer extends Abstract {
    public static final String BOLT_ID = ComponentId.PING_PRODUCER.toString();

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) throws Exception {
        PingContext pingContext = pullPingContext(input);

        // TODO(surabujin): add one switch flow filter
        GroupId group = new GroupId(2);
        emit(input, produce(pingContext, group, FlowDirection.FORWARD));
        emit(input, produce(pingContext, group, FlowDirection.REVERSE));
    }

    private PingContext produce(PingContext pingContext, GroupId group, FlowDirection direction) {
        FlowDto flow = getFlowThread(pingContext.getFlow(), direction);
        Ping ping = new Ping(flow);

        return pingContext.toBuilder()
                .ping(ping)
                .direction(direction)
                .group(group)
                .build();
    }

    private void emit(Tuple input, PingContext pingContext) throws PipelineException {
        CommandContext commandContext = pullContext(input);
        Values output = new Values(pingContext, commandContext);
        getOutput().emit(input, output);
    }

    private FlowDto getFlowThread(BidirectionalFlowDto flow, FlowDirection direction) {
        FlowDto result;

        if (FlowDirection.FORWARD == direction) {
            result = flow.getForward();
        } else if (FlowDirection.REVERSE == direction) {
            result = flow.getReverse();
        } else {
            throw new IllegalArgumentException(String.format(
                    "Unexpected %s value: %s", FlowDirection.class.getCanonicalName(), direction));
        }

        return result;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
