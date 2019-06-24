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

package org.openkilda.wfm.topology.isllatency.bolts;

import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.ISL_GROUPING_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.LATENCY_DATA_FIELD;
import static org.openkilda.wfm.topology.isllatency.IslLatencyTopology.SWITCH_KEY_FIELD;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.IslOneWayLatency;
import org.openkilda.messaging.info.event.IslRoundTripLatency;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.isllatency.model.StreamType;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;


public class RouterBolt extends AbstractBolt {

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof InfoMessage) {
            log.debug("Received info message {}", message);
            InfoData data = ((InfoMessage) message).getData();

            if (data instanceof IslOneWayLatency) {
                handleOneWayLatency(input, (IslOneWayLatency) data);
            } else if (data instanceof IslRoundTripLatency) {
                handleRoundTripLatency(input, (IslRoundTripLatency) data);
            } else {
                unhandledInput(input);
            }
        } else {
            unhandledInput(input);
        }
    }

    private void handleOneWayLatency(Tuple input, IslOneWayLatency data) {
        IslReference islReference = new IslReference(
                Endpoint.of(data.getSrcSwitchId(), data.getSrcPortNo()),
                Endpoint.of(data.getDstSwitchId(), data.getDstPortNo()));
        Values values = new Values(islReference, data, getCommandContext());
        getOutput().emit(StreamType.ONE_WAY_MANIPULATION.toString(), input, values);
    }

    private void handleRoundTripLatency(Tuple input, IslRoundTripLatency data) {
        getOutput().emit(StreamType.CACHE.toString(), input,
                new Values(data.getSrcSwitchId().toString(), data, getCommandContext()));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        Fields oneWayLatencyFields = new Fields(ISL_GROUPING_FIELD, LATENCY_DATA_FIELD, FIELD_ID_CONTEXT);
        declarer.declareStream(StreamType.ONE_WAY_MANIPULATION.toString(), oneWayLatencyFields);
        declarer.declareStream(StreamType.CACHE.toString(),
                new Fields(SWITCH_KEY_FIELD, LATENCY_DATA_FIELD, FIELD_ID_CONTEXT));
    }
}
