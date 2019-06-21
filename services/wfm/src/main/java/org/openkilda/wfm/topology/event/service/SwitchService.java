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

package org.openkilda.wfm.topology.event.service;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwitchService {

    private TransactionManager transactionManager;
    private SwitchRepository switchRepository;

    public SwitchService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        this.switchRepository = repositoryFactory.createSwitchRepository();
    }

    /**
     * Create or update switch in neo4j.
     *
     * @param sw switch.
     */
    public void createOrUpdateSwitch(Switch sw) {
        log.debug("Create or update switch {}", sw.getSwitchId());
        transactionManager.doInTransaction(() -> processCreateOrUpdateSwitch(sw));
    }

    private void processCreateOrUpdateSwitch(Switch sw) {
        Switch daoSwitch = switchRepository.findById(sw.getSwitchId())
                .orElseGet(() -> Switch.builder().switchId(sw.getSwitchId()).build());

        daoSwitch.setAddress(sw.getAddress());
        daoSwitch.setHostname(sw.getHostname());
        daoSwitch.setDescription(sw.getDescription());
        daoSwitch.setController(sw.getController());
        daoSwitch.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(daoSwitch);
    }

    /**
     * Set inactive status in the switch in neo4j.
     *
     * @param sw switch.
     */
    public void deactivateSwitch(Switch sw) {
        log.debug("Deactivate switch {}", sw.getSwitchId());
        transactionManager.doInTransaction(() -> processDeactivateSwitch(sw));
    }

    private void processDeactivateSwitch(Switch sw) {
        Switch daoSwitch = switchRepository.findById(sw.getSwitchId())
                .orElseGet(() -> Switch.builder().switchId(sw.getSwitchId()).build());

        daoSwitch.setStatus(SwitchStatus.INACTIVE);
        switchRepository.createOrUpdate(daoSwitch);
    }

    /**
     * Return switch "Under maintenance" flag.
     * @param switchId switch id.
     */
    public boolean switchIsUnderMaintenance(SwitchId switchId) {
        return switchRepository.findById(switchId)
                .map(Switch::isUnderMaintenance)
                .orElse(false);
    }
}
