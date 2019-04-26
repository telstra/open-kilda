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

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;
import org.openkilda.model.SwitchId;

import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchTracker;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.Set;

@Slf4j
public class RequestBolt extends BaseStatefulBolt<InMemoryKeyValueState<String, SwitchTracker>> {
    private static final String SWITCH_TRACKER_KEY = "SWITCH_TRACKER_KEY";
    protected String outputStream;
    protected final Set<String> regions;

    protected transient SwitchTracker switchTracker;

    protected OutputCollector outputCollector;

    public RequestBolt(String outputStream, Set<String> regions) {
        this.outputStream = outputStream;
        this.regions = regions;

    }

    @Override
    public void execute(Tuple input) {
        try {
            if (Stream.REGION_NOTIFICATION.equals(input.getSourceStreamId())) {
                updateSwitchMapping((SwitchMapping) input.getValueByField(
                        AbstractTopology.MESSAGE_FIELD));
            } else {
                String json = pullRequest(input);
                Message message = MAPPER.readValue(json, Message.class);

                SwitchId switchId = RouterUtils.lookupSwitchIdInCommandMessage(message);
                if (switchId != null) {
                    String region = switchTracker.lookupRegion(switchId);
                    if (region != null) {
                        proxyRequestToSpeaker(input, region);
                    } else {
                        log.error("Unable to lookup region for message: {}", json);
                    }

                } else {
                    log.error("Unable to lookup switch for message: {}", json);
                }
            }
        } catch (Exception e) {
            log.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(input);
        }
    }

    protected void proxyRequestToSpeaker(Tuple input, String region) {
        String targetStream = Stream.formatWithRegion(outputStream, region);
        String key = pullRequestKey(input);
        String value = pullRequest(input);
        outputCollector.emit(targetStream, input, makeSpeakerTuple(key, value));
    }

    protected String pullRequest(Tuple input) {
        return input.getStringByField(KafkaRecordTranslator.FIELD_ID_PAYLOAD);
    }

    protected String pullRequestKey(Tuple input) {
        return input.getStringByField(KafkaRecordTranslator.FIELD_ID_KEY);
    }

    protected Values makeSpeakerTuple(String key, String json) {
        return new Values(key, json);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.outputCollector = outputCollector;
    }

    @Override
    public void initState(InMemoryKeyValueState<String, SwitchTracker> state) {
        SwitchTracker tracker = state.get(SWITCH_TRACKER_KEY);
        if (tracker == null) {
            tracker = new SwitchTracker();
            state.put(SWITCH_TRACKER_KEY, tracker);
        }
        switchTracker = tracker;
    }

    protected void updateSwitchMapping(SwitchMapping mapping) {
        switchTracker.updateRegion(mapping);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        Fields fields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                                   FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        if (regions == null || regions.isEmpty()) {
            outputFieldsDeclarer.declareStream(outputStream, fields);
        } else {
            for (String region : regions) {
                outputFieldsDeclarer.declareStream(Stream.formatWithRegion(outputStream, region), fields);
            }
        }

    }
}
