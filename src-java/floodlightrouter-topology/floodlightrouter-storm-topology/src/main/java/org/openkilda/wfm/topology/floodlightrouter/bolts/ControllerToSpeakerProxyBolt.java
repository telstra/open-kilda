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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.RegionAwareKafkaTopicSelector;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchTracker;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

@Slf4j
public class ControllerToSpeakerProxyBolt extends AbstractBolt {
    private final String targetTopic;

    protected transient SwitchTracker switchTracker;
    protected final Set<String> allRegions;

    public ControllerToSpeakerProxyBolt(String targetTopic, Set<String> allRegions) {
        this.targetTopic = targetTopic;
        this.allRegions = ImmutableSet.copyOf(allRegions);
    }

    @Override
    protected void dispatch(Tuple input) throws Exception {
        if (RegionTrackerBolt.STREAM_REGION_UPDATE_ID.equals(input.getSourceStreamId())) {
            handleSwitchMappingUpdate(pullValue(input, AbstractTopology.MESSAGE_FIELD, SwitchMapping.class));
        } else {
            super.dispatch(input);
        }
    }

    @Override
    public void handleInput(Tuple input) throws Exception {
        handleControllerRequest(pullControllerPayload(input));
    }

    private void handleSwitchMappingUpdate(SwitchMapping mapping) {
        switchTracker.updateRegion(mapping);
    }

    private void handleControllerRequest(Object payload) throws PipelineException {
        if (payload instanceof Message && RouterUtils.isBroadcast((Message) payload)) {
            handleBroadcastRequest((Message) payload);
        } else {
            handleUnicastRequest(payload);
        }
    }

    private void handleBroadcastRequest(Message payload) throws PipelineException {
        for (String region : allRegions) {
            proxyRequestToSpeaker(payload, region);
        }
    }

    private void handleUnicastRequest(Object payload) throws PipelineException {
        SwitchId switchId = lookupSwitchId(payload);
        if (switchId != null) {
            String region = switchTracker.lookupRegion(switchId);
            if (region != null) {
                proxyRequestToSpeaker(payload, region);
            } else {
                handleRegionNotFoundError(payload, switchId);
            }
        } else {
            log.error("Unable to lookup switch for message: {}", payload);
        }
    }

    private SwitchId lookupSwitchId(Object message) {
        SwitchId switchId;

        if (message instanceof AbstractMessage) {
            switchId = RouterUtils.lookupSwitchId((AbstractMessage) message);
        } else if (message instanceof Message) {
            switchId = RouterUtils.lookupSwitchId((Message) message);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Unable to extract switch Id - unknown payload type %s - %s",
                    message.getClass().getName(), message));
        }

        return switchId;
    }

    protected void handleRegionNotFoundError(Object payload, SwitchId switchId) {
        log.error("Unable to route request - region that owns switch {} is unknown (message: {})", switchId, payload);
    }

    protected void proxyRequestToSpeaker(Object payload, String region) throws PipelineException {
        Tuple input = getCurrentTuple();
        String key = pullValue(input, KafkaRecordTranslator.FIELD_ID_KEY, String.class);
        getOutput().emit(input, makeDefaultTuple(payload, key, region));
    }

    protected void init() {
        switchTracker = new SwitchTracker();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        Fields fields = new Fields(
                FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE,
                RegionAwareKafkaTopicSelector.FIELD_ID_TOPIC, RegionAwareKafkaTopicSelector.FIELD_ID_REGION);
        outputFieldsDeclarer.declare(fields);
    }

    protected Object pullControllerPayload(Tuple tuple) throws PipelineException {
        return pullValue(tuple, KafkaRecordTranslator.FIELD_ID_PAYLOAD, Object.class);
    }

    protected Values makeDefaultTuple(Object payload, String key, String region) {
        return new Values(key, payload, targetTopic, region);
    }
}
