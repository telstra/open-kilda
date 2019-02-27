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
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.IllegalSwitchStateException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.SwitchMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SwitchOperationsService {

    private SwitchRepository switchRepository;
    private TransactionManager transactionManager;
    private LinkOperationsService linkOperationsService;
    private IslRepository islRepository;
    private FlowRepository flowRepository;
    private FlowPathRepository flowPathRepository;

    public SwitchOperationsService(RepositoryFactory repositoryFactory,
                                   TransactionManager transactionManager,
                                   int islCostWhenUnderMaintenance) {
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.transactionManager = transactionManager;
        this.linkOperationsService
                = new LinkOperationsService(repositoryFactory, transactionManager, islCostWhenUnderMaintenance);
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
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

    /**
     * Delete switch.
     *
     * @param switchId ID of switch to be deleted
     * @param force if True all switch relationships will be deleted too.
     *              If False switch will be deleted only if it has no relations.
     * @return True if switch was deleted, False otherwise
     * @throws SwitchNotFoundException if switch is not found
     */
    public boolean deleteSwitch(SwitchId switchId, boolean force) throws SwitchNotFoundException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));

        if (force) {
            // forceDelete() removes switch along with all relationships.
            switchRepository.forceDelete(sw.getSwitchId());
        } else {
            // delete() is used to be sure that we wouldn't delete switch if it has even one relationship.
            switchRepository.delete(sw);
        }

        return !switchRepository.exists(switchId);
    }

    /**
     * Check that switch is not in 'Active' state.
     *
     * @throws SwitchNotFoundException if there is no such switch.
     * @throws IllegalSwitchStateException if switch is in 'Active' state
     */
    public void checkSwitchIsDeactivated(SwitchId switchId)
            throws SwitchNotFoundException, IllegalSwitchStateException {
        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));

        if (sw.getStatus() == SwitchStatus.ACTIVE) {
            String message = String.format("Switch '%s' is in 'Active' state.", switchId);
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    /**
     * Check that switch has no Flow relations.
     *
     * @throws IllegalSwitchStateException if switch has Flow relations
     */
    public void checkSwitchHasNoFlows(SwitchId switchId) throws IllegalSwitchStateException {
        Set<String> flowIds = flowRepository.findFlowIdsBySwitch(switchId);

        if (!flowIds.isEmpty()) {
            String message = String.format("Switch '%s' has %d assigned flows: %s.",
                    switchId, flowIds.size(), flowIds);
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    /**
     * Check that switch has no Flow Segment relations.
     *
     * @throws IllegalSwitchStateException if switch has Flow Segment relations
     */
    public void checkSwitchHasNoFlowSegments(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<FlowPath> outgoingFlowPaths = flowPathRepository.findBySegmentSrcSwitchId(switchId);
        Collection<FlowPath> ingoingFlowPaths = flowPathRepository.findBySegmentDestSwitchId(switchId);

        if (!ingoingFlowPaths.isEmpty() || !outgoingFlowPaths.isEmpty()) {
            String message = String.format("Switch '%s' has %d assigned rules. It must be freed first.",
                    switchId, ingoingFlowPaths.size() + outgoingFlowPaths.size());
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    /**
     * Check that switch has no ISL relations.
     *
     * @throws IllegalSwitchStateException if switch has ISL relations
     */
    public void checkSwitchHasNoIsls(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<Isl> outgoingIsls = islRepository.findBySrcSwitch(switchId);
        Collection<Isl> ingoingIsls = islRepository.findByDestSwitch(switchId);

        if (!outgoingIsls.isEmpty() || !ingoingIsls.isEmpty()) {
            long activeIslCount = Stream.concat(outgoingIsls.stream(), ingoingIsls.stream())
                    .filter(isl -> isl.getStatus() == IslStatus.ACTIVE)
                    .count();

            if (activeIslCount > 0) {
                String message = String.format("Switch '%s' has %d active links. Unplug and remove them first.",
                        switchId, activeIslCount);
                throw new IllegalSwitchStateException(switchId.toString(), message);
            } else {
                String message = String.format("Switch '%s' has %d inactive links. Remove them first.",
                        switchId, outgoingIsls.size() + ingoingIsls.size());
                throw new IllegalSwitchStateException(switchId.toString(), message);
            }
        }
    }
}
