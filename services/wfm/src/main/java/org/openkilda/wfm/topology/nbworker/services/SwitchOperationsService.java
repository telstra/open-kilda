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

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.SwitchMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SwitchOperationsService {

    private SwitchRepository switchRepository;
    private TransactionManager transactionManager;
    private LinkOperationsService linkOperationsService;

    public SwitchOperationsService(RepositoryFactory repositoryFactory,
                                   TransactionManager transactionManager,
                                   int islCostWhenUnderMaintenance) {
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.transactionManager = transactionManager;
        this.linkOperationsService
                = new LinkOperationsService(repositoryFactory, transactionManager, islCostWhenUnderMaintenance);
    }

    /**
     * Get switch by switch id.
     *
     * @param switchId switch id.
     */
    public Switch getSwitch(SwitchId switchId) throws SwitchNotFoundException {
        return switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

    /**
     * Return all switches.
     *
     * @return all switches.
     */
    public List<SwitchInfoData> getAllSwitches() {
        return switchRepository.findAll().stream()
                .map(SwitchMapper.INSTANCE::map)
                .collect(Collectors.toList());
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

            linkOperationsService.getAllIsls(switchId, null, null, null)
                    .forEach(isl -> {
                        try {
                            linkOperationsService.updateLinkUnderMaintenanceFlag(
                                    isl.getSrcSwitch().getSwitchId(),
                                    isl.getSrcPort(),
                                    isl.getDestSwitch().getSwitchId(),
                                    isl.getDestPort(),
                                    underMaintenance);
                        } catch (IslNotFoundException e) {
                            //We get all ISLs on this switch, so all ISLs exist
                        }
                    });

            return Optional.of(sw);
        }).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

}
