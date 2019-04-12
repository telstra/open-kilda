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

package org.openkilda.wfm.topology.event;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PortEventRouterBolt extends AbstractBolt {

    public static final String PORT_EVENT_STREAM = "port-event-stream";
    public static final String DEFAULT_STREAM = "default-stream";

    @Override
    protected void handleInput(Tuple tuple) throws PipelineException {
        if (!handlePortEvent(tuple)) {
            getOutput().emit(DEFAULT_STREAM, tuple, new Values(tuple.getString(0)));
        }
    }

    private boolean handlePortEvent(Tuple tuple) throws PipelineException {
        Message message = pullValue(tuple, AbstractTopology.MESSAGE_FIELD, Message.class);
        if (message instanceof InfoMessage) {
            InfoMessage infoMessage = (InfoMessage) message;
            InfoData data = infoMessage.getData();
            if (data instanceof PortInfoData) {
                PortInfoData portInfoData = (PortInfoData) data;
                switch (portInfoData.getState()) {
                    case DOWN:
                    case UP:
                        getOutput().emit(PORT_EVENT_STREAM, tuple,
                                new Values(portInfoData, infoMessage.getCorrelationId(),
                                        String.format("%s_%d", portInfoData.getSwitchId(), portInfoData.getPortNo())));
                        return true;
                    default:
                        return false;
                }
            }
        }
        return false;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(DEFAULT_STREAM, new Fields(PAYLOAD));
        declarer.declareStream(PORT_EVENT_STREAM,
                new Fields(PAYLOAD, CORRELATION_ID, PortEventThrottlingBolt.GROUPING_FIELD_NAME));
    }
}
