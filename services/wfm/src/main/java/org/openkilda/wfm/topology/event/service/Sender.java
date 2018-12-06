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

package org.openkilda.wfm.topology.event.service;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.event.NetworkTopologyBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sender {

    private static final Logger logger = LoggerFactory.getLogger(Sender.class);

    private final OutputCollector outputCollector;
    private final Tuple tuple;
    private final String correlationId;

    public Sender(OutputCollector outputCollector, Tuple tuple, String correlationId) {
        this.outputCollector = outputCollector;
        this.tuple = tuple;
        this.correlationId = correlationId;
    }

    public void sendRerouteInactiveFlowsMessage(String reason) {
        RerouteInactiveFlows request = new RerouteInactiveFlows(reason);
        sendRerouteMessage(request);
    }

    public void sendRerouteAffectedFlowsMessage(SwitchId switchId, int port, String reason) {
        RerouteAffectedFlows request = new RerouteAffectedFlows(new PathNode(switchId, port, 0), reason);
        sendRerouteMessage(request);
    }

    private void sendRerouteMessage(CommandData request) {
        try {
            String json = Utils.MAPPER.writeValueAsString(new CommandMessage(
                    request, System.currentTimeMillis(), correlationId, Destination.WFM_REROUTE));
            Values values = new Values(Utils.PAYLOAD, json);
            outputCollector.emit(NetworkTopologyBolt.REROUTE_STREAM, tuple, values);
        } catch (JsonProcessingException exception) {
            logger.error("Could not format flow reroute request", exception);
        }
    }

}
