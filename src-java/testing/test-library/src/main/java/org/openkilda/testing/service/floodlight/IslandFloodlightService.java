/* Copyright 2021 Telstra Open Source
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

package org.openkilda.testing.service.floodlight;

import static java.util.stream.Collectors.toList;

import org.openkilda.model.SwitchId;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IslandFloodlightService extends FloodlightServiceImpl {
    @Autowired
    TopologyDefinition topology;

    public IslandFloodlightService() {
    }

    public IslandFloodlightService(String endpoint, TopologyDefinition topology) {
        super(endpoint);
        this.topology = topology;
    }

    @Override
    public List<SwitchEntry> getSwitches() {
        List<SwitchEntry> result = super.getSwitches();
        List<SwitchId> switchIds = topology.getSwitches().stream().map(Switch::getDpId).collect(toList());
        return result.stream().filter(sw -> switchIds.contains(sw.getSwitchId())).collect(toList());
    }
}
