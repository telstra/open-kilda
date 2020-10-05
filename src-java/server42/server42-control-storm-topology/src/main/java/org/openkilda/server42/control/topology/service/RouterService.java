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


package org.openkilda.server42.control.topology.service;

import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouterService {
    private final IRouterCarrier carrier;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final SwitchRepository switchRepository;

    public RouterService(IRouterCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
    }

    /**
     * Determinate feature toggler call. Generate messages with SwitchId as key for shuffling.
     * @param featureEnabled flag
     */
    public void handleFlowRttFeatureToggle(Boolean featureEnabled) {
        if (featureEnabled) {
            switchPropertiesRepository.findAll()
                    .stream()
                    .filter(SwitchProperties::isServer42FlowRtt)
                    .forEach(s -> carrier.activateFlowMonitoringOnSwitch(s.getSwitchId()));
        } else {
            switchRepository.findAll()
                    .forEach(s -> carrier.deactivateFlowMonitoringOnSwitch(s.getSwitchId()));
        }
    }
}
