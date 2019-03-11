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

import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Set;

@Slf4j
public class BroadcastRequestBolt extends RequestBolt {
    public BroadcastRequestBolt(String outputStream, Set<String> regions) {
        super(outputStream, regions);
    }

    @Override
    public void execute(Tuple input) {
        try {
            String json = input.getValueByField(AbstractTopology.MESSAGE_FIELD).toString();
            for (String region : regions) {
                String targetStream = Stream.formatWithRegion(outputStream, region);
                Values values = new Values(json);
                outputCollector.emit(targetStream, input, values);
            }
        } catch (Exception e) {
            log.error(String.format("Unhandled exception in %s", getClass().getName()), e);
        } finally {
            outputCollector.ack(input);
        }
    }
}
