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
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchService {

    private static final Logger logger = LoggerFactory.getLogger(SwitchService.class);

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
     * @return transaction was successful or not.
     */
    public boolean createOrUpdateSwitch(Switch sw) {
        logger.info("Create or update switch {}", sw.getSwitchId());
        transactionManager.begin();
        try {
            processCreateOrUpdateSwitch(sw);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }

        return true;
    }

    private void processCreateOrUpdateSwitch(Switch sw) {
        Switch daoSwitch = switchRepository.findBySwitchId(sw.getSwitchId());

        if (daoSwitch == null) {
            daoSwitch = new Switch();
            daoSwitch.setSwitchId(sw.getSwitchId());
        }

        daoSwitch.setAddress(sw.getAddress());
        daoSwitch.setHostname(sw.getHostname());
        daoSwitch.setDescription(sw.getDescription());
        daoSwitch.setController(sw.getController());
        daoSwitch.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(daoSwitch);
    }

    /**
     * Set active status in the switch in neo4j.
     *
     * @param sw switch.
     * @return transaction was successful or not.
     */
    public boolean activateSwitch(Switch sw) {
        logger.info("Activate switch {}", sw.getSwitchId());
        transactionManager.begin();
        try {
            processActivateSwitch(sw);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }
        return true;
    }

    private void processActivateSwitch(Switch sw) {
        Switch daoSwitch = switchRepository.findBySwitchId(sw.getSwitchId());

        if (daoSwitch == null) {
            daoSwitch = new Switch();
            daoSwitch.setSwitchId(sw.getSwitchId());
        }

        daoSwitch.setStatus(SwitchStatus.ACTIVE);
        switchRepository.createOrUpdate(daoSwitch);
    }


    /**
     * Set inactive status in the switch in neo4j.
     *
     * @param sw switch.
     * @return transaction was successful or not.
     */
    public boolean deactivateSwitch(Switch sw) {
        logger.info("Deactivate switch {}", sw.getSwitchId());
        transactionManager.begin();
        try {
            processDeactivateSwitch(sw);
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            return false;
        }
        return true;
    }

    private void processDeactivateSwitch(Switch sw) {
        Switch daoSwitch = switchRepository.findBySwitchId(sw.getSwitchId());

        if (daoSwitch == null) {
            daoSwitch = new Switch();
            daoSwitch.setSwitchId(sw.getSwitchId());
        }

        daoSwitch.setStatus(SwitchStatus.INACTIVE);
        switchRepository.createOrUpdate(daoSwitch);
    }

}
