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

package org.openkilda.wfm.topology.flowhs.bolts;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_REROUTE_HUB;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.UUID;

@Slf4j
public class RouterBolt extends AbstractBolt {

    @Override
    protected void handleInput(Tuple input) {
        String key = input.getStringByField(MessageTranslator.FIELD_ID_KEY);
        if (StringUtils.isBlank(key)) {
            key = UUID.randomUUID().toString();
        }

        CommandMessage message = (CommandMessage) input.getValueByField(MessageTranslator.FIELD_ID_PAYLOAD);
        MessageData data = message.getData();

        if (data instanceof FlowRequest) {
            FlowRequest request = (FlowRequest) data;
            log.debug("Received request {} with key {}", request, key);
            switch (request.getType()) {
                case CREATE:
                    emitWithContext(ROUTER_TO_FLOW_CREATE_HUB.name(), input, new Values(key, request.getPayload()));
                    break;
                default:
                    throw new UnsupportedOperationException(format("Flow operation %s is not supported",
                            request.getType()));
            }
        } else if (data instanceof FlowRerouteRequest) {
            emitWithContext(ROUTER_TO_FLOW_REROUTE_HUB.name(), input, new Values(key, data));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(ROUTER_TO_FLOW_CREATE_HUB.name(), MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_REROUTE_HUB.name(), MessageTranslator.STREAM_FIELDS);
    }
}
