/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import org.openkilda.messaging.AliveResponse;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.topology.floodlightrouter.Stream;

import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

public class SpeakerToNetworkProxyBolt extends SpeakerToControllerProxyBolt {
    public static final String STREAM_REGION_TRACKER_ID = Stream.DISCO_REPLY;
    public static final Fields STREAM_REGION_TRACKER_FIELDS = new Fields(
            FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);

    public SpeakerToNetworkProxyBolt(String outputStream) {
        super(outputStream);
    }

    @Override
    protected void proxy(String key, Object payload) {
        notifyRegionTracker(key, payload);
        if (payload instanceof InfoMessage) {
            proxyInfoMessage(key, (InfoMessage) payload);
        } else {
            super.proxy(key, payload);
        }
    }

    private void proxyInfoMessage(String key, InfoMessage envelope) {
        InfoData payload = envelope.getData();
        if (payload instanceof AliveResponse) {
            // do nothing - alive responses must not be proxied to the controller
        } else {
            super.proxy(key, envelope);
        }
    }

    private void notifyRegionTracker(String key, Object payload) {
        getOutput().emit(STREAM_REGION_TRACKER_ID, getCurrentTuple(), makeRegionTrackerTuple(key, payload));
    }

    private Values makeRegionTrackerTuple(String key, Object payload) {
        return new Values(key, payload);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        super.declareOutputFields(outputFieldsDeclarer);

        outputFieldsDeclarer.declareStream(STREAM_REGION_TRACKER_ID, STREAM_REGION_TRACKER_FIELDS);
    }
}
