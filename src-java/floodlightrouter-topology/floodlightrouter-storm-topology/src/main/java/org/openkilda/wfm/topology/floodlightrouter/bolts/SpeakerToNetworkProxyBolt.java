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
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.floodlightrouter.ComponentType;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.time.Duration;

@Slf4j
public class SpeakerToNetworkProxyBolt extends SpeakerToControllerProxyBolt {
    public static final String BOLT_ID = ComponentType.KILDA_TOPO_DISCO_REPLY_BOLT;

    public static final String FIELD_ID_REGION = "region";
    public static final String FIELD_ID_TIMESTAMP = "timestamp";
    public static final String FIELD_ID_SWITCH_ID = "switch_id";
    public static final String FIELD_ID_PAYLOAD = "payload";

    public static final String STREAM_ALIVE_EVIDENCE_ID = "alive";
    public static final Fields STREAM_ALIVE_EVIDENCE_FIELDS = new Fields(
            FIELD_ID_REGION, FIELD_ID_TIMESTAMP, AbstractBolt.FIELD_ID_CONTEXT);

    public static final String STREAM_REGION_NOTIFICATION_ID = "region";
    public static final Fields STREAM_REGION_NOTIFICATION_FIELDS = new Fields(
            FIELD_ID_REGION, FIELD_ID_PAYLOAD, AbstractBolt.FIELD_ID_CONTEXT);

    public static final String STREAM_CONNECT_NOTIFICATION_ID = "connect";
    public static final Fields STREAM_CONNECT_NOTIFICATION_FIELDS = new Fields(
            FIELD_ID_REGION, FIELD_ID_SWITCH_ID, FIELD_ID_PAYLOAD, AbstractBolt.FIELD_ID_CONTEXT);

    public SpeakerToNetworkProxyBolt(String outputStream, Duration switchMappingRemoveDelay) {
        super(outputStream, switchMappingRemoveDelay);
    }

    @Override
    protected void proxyInfoMessage(String key, InfoMessage envelope) {
        emitAliveEvidence(envelope);

        InfoData payload = envelope.getData();
        if (payload instanceof AliveResponse) {
            emitRegionNotification(envelope.getRegion(), (AliveResponse) payload);
        } else if (payload instanceof SwitchInfoData) {
            emitNetworkNotification(envelope.getRegion(), (SwitchInfoData) payload);
        } else if (payload instanceof NetworkDumpSwitchData) {
            emitNetworkNotification(envelope.getRegion(), (NetworkDumpSwitchData) payload);
        } else if (payload instanceof PortInfoData) {
            emitNetworkNotification(envelope.getRegion(), (PortInfoData) payload);
        } else {
            super.proxyInfoMessage(key, envelope);
        }
    }

    private void emitAliveEvidence(InfoMessage envelope) {
        getOutput().emit(
                STREAM_ALIVE_EVIDENCE_ID, getCurrentTuple(),
                makeAliveEvidenceTuple(envelope.getRegion(), envelope.getTimestamp()));
    }

    private void emitRegionNotification(String region, AliveResponse response) {
        getOutput().emit(
                STREAM_REGION_NOTIFICATION_ID, getCurrentTuple(),
                makeRegionNotificationTuple(region, response));
    }

    private void emitNetworkNotification(String region, SwitchInfoData payload) {
        emitNetworkNotification(region, payload.getSwitchId(), payload);
    }

    private void emitNetworkNotification(String region, NetworkDumpSwitchData payload) {
        emitNetworkNotification(region, payload.getSwitchView().getDatapath(), payload);
    }

    private void emitNetworkNotification(String region, PortInfoData payload) {
        emitNetworkNotification(region, payload.getSwitchId(), payload);
    }

    private void emitNetworkNotification(String region, SwitchId switchId, InfoData payload) {
        getOutput().emit(
                STREAM_CONNECT_NOTIFICATION_ID, getCurrentTuple(),
                makeConnectNotificationTuple(region, switchId, payload));
    }

    private Values makeAliveEvidenceTuple(String region, long timestamp) {
        return new Values(region, timestamp, getCommandContext());
    }

    private Values makeRegionNotificationTuple(String region, InfoData payload) {
        return new Values(region, payload, getCommandContext());
    }

    private Values makeConnectNotificationTuple(String region, SwitchId switchId, InfoData payload) {
        return new Values(region, switchId, payload, getCommandContext());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        super.declareOutputFields(streamManager);

        streamManager.declareStream(STREAM_ALIVE_EVIDENCE_ID, STREAM_ALIVE_EVIDENCE_FIELDS);
        streamManager.declareStream(STREAM_REGION_NOTIFICATION_ID, STREAM_REGION_NOTIFICATION_FIELDS);
        streamManager.declareStream(STREAM_CONNECT_NOTIFICATION_ID, STREAM_CONNECT_NOTIFICATION_FIELDS);
    }
}
