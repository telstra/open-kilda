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

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterUtils;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchTracker;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

@Slf4j
public class RequestBolt extends AbstractBolt {
    protected final String outputStream;
    protected final Set<String> regions;

    protected transient SwitchTracker switchTracker;

    public RequestBolt(String outputStream, Set<String> regions) {
        this.outputStream = outputStream;
        this.regions = regions;

    }

    @Override
    public void handleInput(Tuple input) throws Exception {
        if (Stream.REGION_NOTIFICATION.equals(input.getSourceStreamId())) {
            updateSwitchMapping((SwitchMapping) input.getValueByField(
                    AbstractTopology.MESSAGE_FIELD));
        } else {
            String json = input.getValueByField(AbstractTopology.MESSAGE_FIELD).toString();
            Message message = MAPPER.readValue(json, Message.class);

            SwitchId switchId = RouterUtils.lookupSwitchIdInCommandMessage(message);
            if (switchId != null) {
                String region = switchTracker.lookupRegion(switchId);
                if (region != null) {
                    String targetStream = Stream.formatWithRegion(outputStream, region);
                    Values values = new Values(json);
                    getOutput().emit(targetStream, input, values);
                } else {
                    log.error("Unable to lookup region for message: {}", json);
                }

            } else {
                log.error("Unable to lookup switch for message: {}", json);
            }
        }
    }

    protected void init() {
        switchTracker = new SwitchTracker();
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
