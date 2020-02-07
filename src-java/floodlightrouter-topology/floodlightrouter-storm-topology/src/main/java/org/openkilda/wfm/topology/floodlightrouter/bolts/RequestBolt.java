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
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchTracker;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

@Slf4j
public class RequestBolt extends AbstractBolt {
    protected final String outputMessageStream;
    protected final String outputAbstractMessageStream;
    protected final Set<String> regions;

    protected transient SwitchTracker switchTracker;

    public RequestBolt(String outputMessageStream, Set<String> regions) {
        this.outputMessageStream = outputMessageStream;
        this.outputAbstractMessageStream = null;
        this.regions = regions;
    }

    public RequestBolt(String outputMessageStream, String outputAbstractMessageStream, Set<String> regions) {
        this.outputMessageStream = outputMessageStream;
        this.outputAbstractMessageStream = outputAbstractMessageStream;
        this.regions = regions;
    }

    @Override
    public void handleInput(Tuple input) throws Exception {
        if (Stream.REGION_NOTIFICATION.equals(input.getSourceStreamId())) {
            handleSwitchMappingUpdate(pullValue(input, AbstractTopology.MESSAGE_FIELD, SwitchMapping.class));
        } else {
            handleControllerRequest(input);
        }
    }

    private void handleSwitchMappingUpdate(SwitchMapping mapping) {
        updateSwitchMapping(mapping);
    }

    private void handleControllerRequest(Tuple input) throws PipelineException {
        Object message = pullRequest(input);
        SwitchId switchId = lookupSwitchId(message);
        if (switchId != null) {
            String region = switchTracker.lookupRegion(switchId);
            if (region != null) {
                proxyRequestToSpeaker(input, region);
            } else {
                log.error("Unable to lookup region for message: {}", message);
            }
        } else {
            log.error("Unable to lookup switch for message: {}", message);
        }
    }

    private SwitchId lookupSwitchId(Object message) {
        SwitchId switchId;

        if (message instanceof AbstractMessage) {
            switchId = RouterUtils.lookupSwitchId((AbstractMessage) message);
        } else {
            switchId = RouterUtils.lookupSwitchId((Message) message);
        }

        return switchId;
    }

    protected void proxyRequestToSpeaker(Tuple input, String region) throws PipelineException {
        String targetStream = Stream.formatWithRegion(outputMessageStream, region);
        String key = pullRequestKey(input);
        Object value = pullRequest(input);
        if (value instanceof AbstractMessage) {
            targetStream = Stream.formatWithRegion(outputAbstractMessageStream, region);
        }
        getOutput().emit(targetStream, input, makeSpeakerTuple(key, value));
    }

    protected Object pullRequest(Tuple input) throws PipelineException {
        return pullValue(input, KafkaRecordTranslator.FIELD_ID_PAYLOAD, Object.class);
    }

    protected String pullRequestKey(Tuple input) {
        return input.getStringByField(KafkaRecordTranslator.FIELD_ID_KEY);
    }

    protected Values makeSpeakerTuple(String key, Object message) {
        return new Values(key, message);
    }

    protected void init() {
        switchTracker = new SwitchTracker();
    }

    protected void updateSwitchMapping(SwitchMapping mapping) {
        switchTracker.updateRegion(mapping);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                                   FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        if (regions == null || regions.isEmpty()) {
            outputFieldsDeclarer.declareStream(outputMessageStream, fields);
            outputFieldsDeclarer.declareStream(outputAbstractMessageStream, fields);
        } else {
            for (String region : regions) {
                outputFieldsDeclarer.declareStream(Stream.formatWithRegion(outputMessageStream, region), fields);
                outputFieldsDeclarer.declareStream(
                        Stream.formatWithRegion(outputAbstractMessageStream, region), fields);
            }
        }

    }
}
