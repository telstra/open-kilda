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

package org.openkilda.wfm.topology.flow.bolts;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.flow.UniFlowVerificationResponse;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.ComponentType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerificationBolt extends AbstractBolt {
    public static final String FIELD_ID_FLOW_ID = Utils.FLOW_ID;
    public static final String FIELD_ID_OUTPUT = "payload";
    public static final String FIELD_ID_INPUT = AbstractTopology.MESSAGE_FIELD;

    public static final String STREAM_ID_PROXY = "proxy";
    public static final Fields STREAM_FIELDS_PROXY = new Fields(FIELD_ID_FLOW_ID, FIELD_ID_OUTPUT, FIELD_ID_INPUT);

    private static final Logger logger = LoggerFactory.getLogger(VerificationBolt.class);

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_ID_PROXY, STREAM_FIELDS_PROXY);
    }

    @Override
    protected void handleInput(Tuple input) {
        String source = input.getSourceComponent();

        if (source.equals(ComponentType.CRUD_BOLT.toString())) {
            proxyRequest(input);
        } else if (source.equals(ComponentType.SPEAKER_BOLT.toString())) {
            consumePingReply(input);
        } else {
            logger.warn("Unexpected input from {} - is topology changes without code change?", source);
        }
    }

    private void proxyRequest(Tuple input) {
        Values proxyData = new Values(
                input.getValueByField(CrudBolt.FIELD_ID_FLOW_ID),
                input.getValueByField(CrudBolt.FIELD_ID_BIFLOW),
                input.getValueByField(CrudBolt.FIELD_ID_MESSAGE));
        getOutput().emit(STREAM_ID_PROXY, input, proxyData);
    }

    private void consumePingReply(Tuple input) {
        UniFlowVerificationResponse response = fetchUniFlowResponse(input);
        Values payload = new Values(response.getFlowId(), response, null);
        getOutput().emit(STREAM_ID_PROXY, input, payload);
    }

    private UniFlowVerificationResponse fetchUniFlowResponse(Tuple input) {
        UniFlowVerificationResponse value;
        try {
            value = (UniFlowVerificationResponse) input.getValueByField(SpeakerBolt.FIELD_ID_PAYLOAD);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    String.format("Can't deserialize into %s", UniFlowVerificationResponse.class.getName()), e);
        }

        return value;
    }
}
