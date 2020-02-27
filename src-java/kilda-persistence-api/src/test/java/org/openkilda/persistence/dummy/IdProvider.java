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

package org.openkilda.persistence.dummy;

import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class IdProvider {
    private long flowCounter = 0;
    private long flowEffectiveIdCounter = 1;
    private long pathCounter = 0;

    private int transitVlanCounter = 1;
    private int transitVxLanCounter = 1;

    private Map<SwitchId, Integer> meterCounters = new HashMap<>();

    public String provideFlowId() {
        return String.format("flow-dummy-%d", flowCounter++);
    }

    public long provideFlowEffectiveId() {
        return flowEffectiveIdCounter++;
    }

    public PathId providePathId(String flowId, Stream<String> tags) {
        String allTags = tags.collect(Collectors.joining("-"));
        if (! allTags.isEmpty()) {
            allTags = "path";
        }
        return new PathId(String.format("%s-(%s-%d)", flowId, allTags, pathCounter++));
    }

    public int provideTransitVlanId() {
        return transitVlanCounter++;
    }

    public int provideTransitVxLanId() {
        return transitVxLanCounter++;
    }

    public MeterId provideMeterId(SwitchId switchId) {
        Integer counter = meterCounters.getOrDefault(switchId, MeterId.MIN_FLOW_METER_ID);
        MeterId meterId = new MeterId(counter);
        meterCounters.put(switchId, counter + 1);
        return meterId;
    }
}
