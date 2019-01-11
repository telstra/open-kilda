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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.IllegalSwitchStateException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.SwitchMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SwitchOperationsService {

    private SwitchRepository switchRepository;
    private IslRepository islRepository;
    private FlowRepository flowRepository;
    private FlowSegmentRepository flowSegmentRepository;

    public SwitchOperationsService(RepositoryFactory repositoryFactory) {
        this.switchRepository = repositoryFactory.createSwitchRepository();
        islRepository = repositoryFactory.createIslRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
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
     * Check that switch can be removed. It includes following checks:
     * - switch is exist
     * - switch is deactivated
     * - switch has no flows
     * - switch has no flow segments
     * - switch has no ISLs
     *
     * @throws SwitchNotFoundException if there is no such switch.
     * @throws IllegalSwitchStateException if switch is in active state or it has any relation.
     */

    public void checkSwitchBeforeDelete(SwitchId switchId) throws SwitchNotFoundException, IllegalSwitchStateException {
        checkSwitchIsDeactivated(switchId);
        checkSwitchHasNoFlows(switchId);
        checkSwitchHasNoFlowSegments(switchId);
        checkSwitchHasNoIsls(switchId);
    }

    /**
     * Delete switch.
     *
     * @param switchId ID of switch to be deleted
     * @param force if True all switch relationships will be deleted too.
     *              If False switch will be deleted only if it has no relations.
     *
     * @return True if switch was deleted, False otherwise
     * @throws SwitchNotFoundException if switch is not found
     */
    public boolean deleteSwitch(SwitchId switchId, boolean force) throws SwitchNotFoundException {
        // ClientException
        // Catching an exception here usually means that we missed some check before delete
        // and switch node still has relationships.
        Optional<Switch> sw = switchRepository.findById(switchId);

        if (!sw.isPresent()) {
            throw new SwitchNotFoundException(switchId);
        }

        if (force) {
            // forceDelete() removes switch along with all relationships.
            switchRepository.forceDelete(sw.get().getSwitchId());
        } else {
            // delete() is used to be sure that we wouldn't delete switch if it has even one relationship.
            switchRepository.delete(sw.get());
        }

        return !switchRepository.exists(switchId);
    }

    private void checkSwitchIsDeactivated(SwitchId switchId)
            throws SwitchNotFoundException, IllegalSwitchStateException {
        Optional<Switch> sw = switchRepository.findById(switchId);

        if (!sw.isPresent()) {
            log.error("Could not delete switch '{}'. Switch not found.", switchId);
            throw new SwitchNotFoundException(switchId);
        }

        if (sw.get().getStatus() == SwitchStatus.ACTIVE) {
            String message = String.format("Could not delete switch '%s' because it is 'Active' state. "
                    + "Switch must be deactivated before delete.", switchId);
            log.error(message);
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    private void checkSwitchHasNoFlows(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<Flow> outgoingFlows = flowRepository.findBySrcSwitchId(switchId);
        Collection<Flow> ingoingFlows = flowRepository.findByDstSwitchId(switchId);

        if (!outgoingFlows.isEmpty() || !ingoingFlows.isEmpty()) {
            List<String> flowIds = Stream.concat(ingoingFlows.stream(), outgoingFlows.stream())
                    .map(Flow::getFlowId)
                    .collect(Collectors.toList());

            String message = String.format("Could not delete switch '%s'. Switch has %d assigned flows: %s "
                    + "They must be removed first.", switchId, flowIds.size(), flowIds);
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    private void checkSwitchHasNoFlowSegments(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<FlowSegment> outgoingFlowSegments = flowSegmentRepository.findBySrcSwitchId(switchId);
        Collection<FlowSegment> ingoingFlowSegments = flowSegmentRepository.findByDestSwitchId(switchId);

        if (!ingoingFlowSegments.isEmpty() || !outgoingFlowSegments.isEmpty()) {
            String message = String.format("Could not delete switch '%s'. Switch has %d assigned rules. "
                    + "It must be freed first.", switchId, ingoingFlowSegments.size() + outgoingFlowSegments.size());
            throw new IllegalSwitchStateException(switchId.toString(), message);
        }
    }

    private void checkSwitchHasNoIsls(SwitchId switchId) throws IllegalSwitchStateException {
        Collection<Isl> outgoingIsls = islRepository.findBySrcSwitch(switchId);
        Collection<Isl> ingoingIsls = islRepository.findByDestSwitch(switchId);

        if (!outgoingIsls.isEmpty() || !ingoingIsls.isEmpty()) {
            long activeIslCount = Stream.concat(outgoingIsls.stream(), ingoingIsls.stream())
                    .filter(isl -> isl.getStatus() == IslStatus.ACTIVE)
                    .count();

            if (activeIslCount > 0) {
                String message = String.format("Could not delete switch '%s'. Switch has %d active links. "
                        + "Unplug and remove them first.", switchId, activeIslCount);
                throw new IllegalSwitchStateException(switchId.toString(), message);
            } else {
                String message = String.format("Could not delete switch '%s'. Switch has %d inactive links. "
                        + "Remove them first.", switchId, outgoingIsls.size() + ingoingIsls.size());
                throw new IllegalSwitchStateException(switchId.toString(), message);
            }
        }
    }
}
