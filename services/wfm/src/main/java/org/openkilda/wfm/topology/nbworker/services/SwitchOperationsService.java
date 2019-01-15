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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class SwitchOperationsService {

    private SwitchRepository switchRepository;
    private TransactionManager transactionManager;

    public SwitchOperationsService(RepositoryFactory repositoryFactory, TransactionManager transactionManager) {
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.transactionManager = transactionManager;
    }

    /**
     * Update the "Under maintenance" flag for the switch.
     *
     * @param switchId switch id.
     * @param underMaintenance "Under maintenance" flag.
     * @return updated switch.
     * @throws SwitchNotFoundException if there is no switch with this switch id.
     */
    public Switch updateSwitchUnderMaintenanceFlag(SwitchId switchId, boolean underMaintenance)
            throws SwitchNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Optional<Switch> foundSwitch = switchRepository.findById(switchId);
            if (!(foundSwitch.isPresent())) {
                return Optional.<Switch>empty();
            }

            Switch sw = foundSwitch.get();

            if (sw.isUnderMaintenance() == underMaintenance) {
                return Optional.of(sw);
            }

            sw.setUnderMaintenance(underMaintenance);

            switchRepository.createOrUpdate(sw);

            return Optional.of(sw);
        }).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

}
