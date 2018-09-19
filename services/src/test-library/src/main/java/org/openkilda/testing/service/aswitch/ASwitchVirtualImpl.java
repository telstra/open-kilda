/* Copyright 2018 Telstra Open Source
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

package org.openkilda.testing.service.aswitch;

import static org.openkilda.testing.Constants.ASWITCH_NAME;
import static org.openkilda.testing.Constants.VIRTUAL_CONTROLLER_ADDRESS;

import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.service.aswitch.model.ASwitchFlow;
import org.openkilda.testing.service.mininet.Mininet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("virtual")
@Slf4j
public class ASwitchVirtualImpl implements ASwitchService {
    @Autowired
    private Mininet mininet;
    @Autowired
    private TopologyDefinition topology;

    @Override
    public void addFlows(List<ASwitchFlow> flows) {
        flows.forEach(flow -> mininet.addFlow(ASWITCH_NAME, flow.getInPort(), flow.getOutPort()));
        log.info("Added flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public void removeFlows(List<ASwitchFlow> flows) {
        flows.forEach(flow -> mininet.removeFlow(ASWITCH_NAME, flow.getInPort()));
        log.info("Removed flows: {}", flows.stream()
                .map(flow -> String.format("%s->%s", flow.getInPort(), flow.getOutPort()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<ASwitchFlow> getAllFlows() {
        throw new UnsupportedOperationException("getAllFlows operation for a-switch is not available on virtual env");
    }

    @Override
    public void portsUp(List<Integer> ports) {
        ports.forEach(port -> mininet.portUp(ASWITCH_NAME, port));
    }

    @Override
    public void portsDown(List<Integer> ports) {
        ports.forEach(port -> mininet.portDown(ASWITCH_NAME, port));
    }

    @Override
    public void knockoutSwitch(String switchId) {
        mininet.knockoutSwitch(topology.getSwitches().stream()
                .filter(sw -> sw.getDpId().toString().equals(switchId)).findFirst().get().getName());
    }

    @Override
    public void reviveSwitch(String switchId, String controllerAddress) {
        String switchName = topology.getSwitches().stream()
                .filter(sw -> sw.getDpId().toString().equals(switchId)).findFirst().get().getName();
        mininet.revive(switchName, VIRTUAL_CONTROLLER_ADDRESS);
    }
}
