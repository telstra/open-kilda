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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.protocol.BoltToBoltMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.io.IOException;

public class RouteMessage extends BoltToBoltMessage<RequestData> {
    public static final String FIELD_ID_TOPOLOGY = "topology";
    public static final Fields FORMAT = new Fields(FIELD_ID_JSON, FIELD_ID_TOPOLOGY, FIELD_ID_CORRELATION_ID);

    private final String topology;

    public RouteMessage(Tuple raw) throws MessageFormatException {
        super(raw);
        topology = raw.getString(getFormat().fieldIndex(FIELD_ID_TOPOLOGY));
    }

    public RouteMessage(RequestData payload, String correlationId, String topology) {
        super(payload, correlationId);
        this.topology = topology;
    }

    @Override
    protected RequestData unpackJson(String json) throws IOException {
        return Utils.MAPPER.readValue(json, RequestData.class);
    }

    @Override
    protected Object packField(String fieldId) throws JsonProcessingException {
        if (fieldId.equals(FIELD_ID_TOPOLOGY)) {
            return getTopology();
        }
        return super.packField(fieldId);
    }

    @Override
    protected Fields getFormat() {
        return FORMAT;
    }

    public String getTopology() {
        return topology;
    }
}
