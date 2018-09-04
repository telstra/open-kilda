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

package org.openkilda.wfm;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.topology.cache.StreamType;
import org.openkilda.wfm.topology.cache.service.ISender;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class Sender implements ISender {
    private final OutputCollector outputCollector;
    private final Tuple tuple;

    public Sender(OutputCollector outputCollector, Tuple tuple) {
        this.outputCollector = outputCollector;
        this.tuple = tuple;
    }

    @Override
    public void sendInfoToTopologyEngine(InfoData data, String correlationId) throws JsonProcessingException {
        Message message = new InfoMessage(data, System.currentTimeMillis(), correlationId, Destination.TOPOLOGY_ENGINE);
        send(StreamType.TPE.toString(), message);
    }

    @Override
    public void sendInfoToWfmReroute(InfoData data, String correlationId) throws JsonProcessingException {
        Message message = new InfoMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
        send(StreamType.WFM_REROUTE.toString(), message);
    }

    @Override
    public void sendCommandToWfmReroute(String flowId, String correlationId) {
        outputCollector.emit(StreamType.WFM_REROUTE.toString(), tuple, new Values(flowId, correlationId));
    }

    protected void send(String stream, Object message) throws JsonProcessingException {
        outputCollector.emit(stream, tuple, new Values(MAPPER.writeValueAsString(message)));
    }
}
