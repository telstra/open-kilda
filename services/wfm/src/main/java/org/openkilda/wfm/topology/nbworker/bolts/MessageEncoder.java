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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.event.DeactivateIslInfoData;
import org.openkilda.messaging.info.event.DeactivateSwitchInfoData;
import org.openkilda.messaging.info.event.IslBfdFlagUpdated;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.share.bolt.KafkaEncoder;
import org.openkilda.wfm.topology.nbworker.StreamType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class MessageEncoder extends KafkaEncoder {

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        MessageData payload = pullPayload(input);
        try {
            Message message = wrap(pullContext(input), payload);

            if (payload instanceof FlowRerouteRequest) {
                getOutput().emit(StreamType.REROUTE.toString(), input, new Values(message));
            } else if (routeIntoDisco(payload)) {
                getOutput().emit(StreamType.DISCO.toString(), input, new Values(message));
            } else if (payload instanceof ErrorData) {
                getOutput().emit(StreamType.ERROR.toString(), input, new Values(null, message));
            }
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
            unhandledInput(input);
        }
    }

    private boolean routeIntoDisco(MessageData payload) {
        return payload instanceof DeactivateIslInfoData
                || payload instanceof DeactivateSwitchInfoData
                || payload instanceof IslBfdFlagUpdated;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(StreamType.REROUTE.toString(), new Fields("message"));
        outputManager.declareStream(StreamType.DISCO.toString(), new Fields("message"));
        outputManager.declareStream(StreamType.ERROR.toString(), STREAM_FIELDS);
    }
}
