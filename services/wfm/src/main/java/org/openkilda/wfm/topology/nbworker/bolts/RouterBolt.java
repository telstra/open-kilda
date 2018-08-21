/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.FlowsBaseRequest;
import org.openkilda.messaging.nbtopology.request.LinksBaseRequest;
import org.openkilda.messaging.nbtopology.request.SwitchesBaseRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.nbworker.StreamType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RouterBolt extends AbstractBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouterBolt.class);

    @Override
    protected void handleInput(Tuple input) {
        String request = input.getString(0);

        Message message;
        try {
            message = Utils.MAPPER.readValue(request, Message.class);
        } catch (IOException e) {
            LOGGER.error("Error during parsing request for NBWorker topology", e);
            return;
        }

        if (message instanceof CommandMessage) {
            LOGGER.debug("Received command message {}", message);
            CommandMessage command = (CommandMessage) message;
            CommandData data = command.getData();

            if (data instanceof BaseRequest) {
                BaseRequest baseRequest = (BaseRequest) data;
                processRequest(input, baseRequest, message.getCorrelationId());
            }
        } else {
            unhandledInput(input);
        }
    }

    private void processRequest(Tuple input, BaseRequest request, String correlationId) {
        if (request instanceof SwitchesBaseRequest) {
            getOutput().emit(StreamType.SWITCH.toString(), input, new Values(request, correlationId));
        } else if (request instanceof LinksBaseRequest) {
            getOutput().emit(StreamType.ISL.toString(), input, new Values(request, correlationId));
        } else if (request instanceof FlowsBaseRequest) {
            getOutput().emit(StreamType.FLOW.toString(), input, new Values(request, correlationId));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(StreamType.SWITCH.toString(), new Fields("request", "correlationId"));
        declarer.declareStream(StreamType.ISL.toString(), new Fields("request", "correlationId"));
        declarer.declareStream(StreamType.FLOW.toString(), new Fields("request", "correlationId"));
    }
}
