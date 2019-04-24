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

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchTracker;

import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.io.IOException;
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
                String json = input.getValueByField(AbstractTopology.MESSAGE_FIELD).toString();
                try {
                    handleMessage(json, input);
                    return;
                } catch (JsonMappingException e) {
                    log.debug("Failed to deserialize json to message");
                }

                handleAbstractMessage(json, input);
            }
        } catch (Exception e) {
            log.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(input);
        }
    }

    private void handleMessage(String json, Tuple input) throws IOException {
        Message message = MAPPER.readValue(json, Message.class);

        SwitchId switchId = RouterUtils.lookupSwitchIdInCommandMessage(message);
        if (switchId != null) {
            String region = switchTracker.lookupRegion(switchId);
            if (region != null) {
                String targetStream = Stream.formatWithRegion(outputStream, region);
                Values values = new Values(json);
                outputCollector.emit(targetStream, input, values);
            } else {
                log.error("Unable to lookup region for message: {}", json);
            }
        } else {
            log.error("Unable to lookup switch for message: {}", json);
        }
    }

    void handleAbstractMessage(String json, Tuple input) throws IOException {
        AbstractMessage message = MAPPER.readValue(json, AbstractMessage.class);
        SwitchId switchId = RouterUtils.lookupSwitchIdInMessage(message);
        if (switchId != null) {
            String region = switchTracker.lookupRegion(switchId);
            if (region != null) {
                String targetStream = Stream.formatWithRegion(outputStream, region);
                Values values = new Values(json);
                outputCollector.emit(targetStream, input, values);
            } else {
                log.error("Unable to lookup region for abstract message: {}", json);
            }
        } else {
            log.error("Unable to lookup switch for abstract message: {}", json);
        }
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
        Fields fields = new Fields(AbstractTopology.MESSAGE_FIELD);
        if (regions == null || regions.isEmpty()) {
            outputFieldsDeclarer.declareStream(outputStream, fields);
        } else {
            for (String region : regions) {
                outputFieldsDeclarer.declareStream(Stream.formatWithRegion(outputStream, region), fields);
            }
        }

    }
}
