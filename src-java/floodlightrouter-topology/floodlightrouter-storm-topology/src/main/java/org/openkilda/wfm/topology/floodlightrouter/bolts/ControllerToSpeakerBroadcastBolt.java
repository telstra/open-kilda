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

import org.openkilda.wfm.error.PipelineException;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.tuple.Tuple;

import java.time.Duration;
import java.util.Set;

// FIXME(surabujin) - used exclusively by statsrouter must be dropped (safe to do blind broadcast).
@Slf4j
public class ControllerToSpeakerBroadcastBolt extends ControllerToSpeakerProxyBolt {
    public ControllerToSpeakerBroadcastBolt(String targetTopic, Set<String> regions) {
        super(targetTopic, regions, Duration.ZERO);
    }

    @Override
    public void handleInput(Tuple input) throws PipelineException {
        Object payload = pullControllerPayload(input);
        for (String region : allRegions) {
            proxyRequestToSpeaker(payload, region);
        }
    }
}
