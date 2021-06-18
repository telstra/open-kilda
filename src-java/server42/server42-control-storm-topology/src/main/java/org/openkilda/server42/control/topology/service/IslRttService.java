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

package org.openkilda.server42.control.topology.service;

import static java.util.Collections.emptySet;

import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class IslRttService {
    private final IslCarrier carrier;
    private final FeatureTogglesRepository featureTogglesRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final IslRepository islRepository;

    public IslRttService(IslCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        islRepository = repositoryFactory.createIslRepository();
    }

    /**
     * Activate monitoring for all existed ISLs on provided switch.
     *
     * @param switchId switch id
     */
    public void activateIslMonitoringForSwitch(SwitchId switchId) {
        if (isIslRttFeatureToggle() && isIslRttFeatureEnabledFor(switchId)) {
            islRepository.findBySrcSwitch(switchId).stream()
                    .filter(isl -> isIslRttFeatureEnabledFor(isl.getDestSwitchId()))
                    .forEach(isl -> carrier.notifyActivateIslMonitoring(isl.getSrcSwitchId(), isl.getSrcPort()));
        } else {
            log.info("Skip activation of ISL RTT for switch:{}", switchId);
        }
    }

    /**
     * Send list with ISL RTT ports on provided switch.
     *
     * @param switchId switch id
     */
    public void sendIslPortListOnSwitchCommand(SwitchId switchId) {
        Set<Integer> islPortsOnSwitch;

        if (isIslRttFeatureToggle() && isIslRttFeatureEnabledFor(switchId)) {
            islPortsOnSwitch = islRepository.findBySrcSwitch(switchId).stream()
                    .filter(isl -> isIslRttFeatureEnabledFor(isl.getDestSwitchId()))
                    .map(Isl::getSrcPort)
                    .collect(Collectors.toSet());
        } else {
            islPortsOnSwitch = emptySet();
        }
        carrier.sendListOfIslPortsBySwitchId(switchId, islPortsOnSwitch);
    }

    private boolean isIslRttFeatureToggle() {
        return featureTogglesRepository.getOrDefault().getServer42IslRtt();
    }

    private boolean isIslRttFeatureEnabledFor(SwitchId switchId) {
        return switchPropertiesRepository.findBySwitchId(switchId)
                .map(SwitchProperties::hasServer42IslRttEnabled)
                .orElse(false);
    }
}
