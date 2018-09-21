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

package org.openkilda.wfm.ctrl;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.ctrl.CtrlRequest;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.IKildaBolt;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.protocol.KafkaMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import org.apache.storm.tuple.Tuple;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;

public class RouteAction extends AbstractAction {
    private String topologyName;
    private Map<String, String> endpoints;

    public RouteAction(
            IKildaBolt bolt, Tuple tuple,
            String topologyName, Map<String, String> endpoint) {
        super(bolt, tuple);

        this.topologyName = topologyName;
        this.endpoints = endpoint;
    }

    @Override
    protected void handle() throws MessageFormatException, JsonProcessingException {
        KafkaMessage input = new KafkaMessage(getTuple());
        Message payload = input.getPayload();

        if (! (payload instanceof CtrlRequest)) {
            log.debug(String.format(
                    "Skip foreign message (correlation-id: %s timestamp: %s)",
                    payload.getCorrelationId(), payload.getTimestamp()));
            return;
        }

        handleMessage((CtrlRequest) payload);
    }

    private void handleMessage(CtrlRequest payload) throws JsonProcessingException {
        RouteMessage message = new RouteMessage(
                payload.getData(), payload.getCorrelationId(), topologyName);
        List<Object> packedMessage = message.pack();

        String glob = payload.getRoute();

        if (Strings.isNullOrEmpty(glob)) {
            glob = "**";
        } else if (glob.equals("*")) {
            glob = "**";
        }

        FileSystem fs = FileSystems.getDefault();
        PathMatcher matcher = fs.getPathMatcher("glob:" + glob);

        for (String bolt : endpoints.keySet()) {
            Path route = fs.getPath(topologyName, bolt);

            if (! matcher.matches(route)) {
                continue;
            }

            getOutputCollector().emit(
                    endpoints.get(bolt), getTuple(), packedMessage);
        }
    }
}
