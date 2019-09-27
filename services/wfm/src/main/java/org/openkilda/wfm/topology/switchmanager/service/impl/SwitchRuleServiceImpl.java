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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.command.switches.SwitchRulesInstallRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.model.FlowPath;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.topology.switchmanager.service.SwitchManagerCarrier;
import org.openkilda.wfm.topology.switchmanager.service.SwitchRuleService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SwitchRuleServiceImpl implements SwitchRuleService {

    private SwitchManagerCarrier carrier;
    private FlowPathRepository flowPathRepository;
    private SwitchPropertiesRepository switchPropertiesRepository;
    private IslRepository islRepository;

    public SwitchRuleServiceImpl(SwitchManagerCarrier carrier, RepositoryFactory repositoryFactory) {
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        islRepository = repositoryFactory.createIslRepository();
        this.carrier = carrier;
    }

    @Override
    public void deleteRules(String key, SwitchRulesDeleteRequest data) {

        SwitchId switchId = data.getSwitchId();
        Optional<SwitchProperties> switchProperties = switchPropertiesRepository.findBySwitchId(switchId);
        if (switchProperties.isPresent()) {
            data.setMultiTable(switchProperties.get().isMultiTable());
            Collection<FlowPath> flowPaths = flowPathRepository.findBySrcSwitch(switchId);
            List<Integer> flowPorts = new ArrayList<>();
            for (FlowPath flowPath : flowPaths) {
                if (flowPath.isForward() && flowPath.getFlow().isSrcWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getSrcPort());
                } else if (!flowPath.isForward() && flowPath.getFlow().isDestWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getDestPort());
                }
            }
            data.setFlowPorts(flowPorts);
            List<Integer> islPorts = islRepository.findBySrcSwitch(switchId).stream()
                    .map(isl -> isl.getSrcPort())
                    .collect(Collectors.toList());
            data.setIslPorts(islPorts);
        }
        carrier.sendCommandToSpeaker(key, data);
    }

    @Override
    public void installRules(String key, SwitchRulesInstallRequest data) {

        SwitchId switchId = data.getSwitchId();
        Optional<SwitchProperties> switchProperties = switchPropertiesRepository.findBySwitchId(switchId);
        if (switchProperties.isPresent()) {
            data.setMultiTable(switchProperties.get().isMultiTable());
            Collection<FlowPath> flowPaths = flowPathRepository.findBySrcSwitch(switchId);
            List<Integer> flowPorts = new ArrayList<>();
            for (FlowPath flowPath : flowPaths) {
                if (flowPath.isForward() && flowPath.getFlow().isSrcWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getSrcPort());
                } else if (!flowPath.isForward() && flowPath.getFlow().isDestWithMultiTable()) {
                    flowPorts.add(flowPath.getFlow().getDestPort());
                }
            }
            data.setFlowPorts(flowPorts);
            List<Integer> islPorts = islRepository.findBySrcSwitch(switchId).stream()
                    .map(isl -> isl.getSrcPort())
                    .collect(Collectors.toList());
            data.setIslPorts(islPorts);
        }
        carrier.sendCommandToSpeaker(key, data);
    }

    @Override
    public void rulesResponse(String key, SwitchRulesResponse response) {
        carrier.cancelTimeoutCallback(key);
        InfoMessage message = new InfoMessage(response, System.currentTimeMillis(), key);

        carrier.response(key, message);
    }
}
