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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.lang.String.format;
import static org.openkilda.model.SwitchFeature.BFD;
import static org.openkilda.model.SwitchFeature.LAG;

import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.InvalidDataException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.service.LagOperationService;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LagOperationServiceImpl implements LagOperationService {
    private final TransactionManager transactionManager;
    private final SwitchRepository switchRepository;
    private final LagLogicalPortRepository lagLogicalPortRepository;
    private final PhysicalPortRepository physicalPortRepository;
    private final int bfdPortOffset;
    private final int bfdPortMaxNumber;
    private final int lagPortOffset;

    public LagOperationServiceImpl(RepositoryFactory repositoryFactory, TransactionManager transactionManager,
                                   int bfdPortOffset, int bfdPortMaxNumber, int lagPortOffset) {
        this.transactionManager = transactionManager;
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        this.physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
        this.bfdPortOffset = bfdPortOffset;
        this.bfdPortMaxNumber = bfdPortMaxNumber;
        this.lagPortOffset = lagPortOffset;
    }

    @Override
    public int createLagPort(SwitchId switchId, List<Integer> physicalPortNumbers) {
        int lagLogicalPortNumber = LagLogicalPort.generateLogicalPortNumber(physicalPortNumbers, lagPortOffset);
        LagLogicalPort lagLogicalPort = new LagLogicalPort(switchId, lagLogicalPortNumber);
        List<PhysicalPort> physicalPorts = physicalPortNumbers.stream()
                .map(port -> new PhysicalPort(switchId, port, lagLogicalPort)).collect(Collectors.toList());
        lagLogicalPort.setPhysicalPorts(physicalPorts);

        lagLogicalPortRepository.add(lagLogicalPort);
        return lagLogicalPortNumber;
    }

    @Override
    public void removeCreatedLagPort(SwitchId switchId, List<Integer> physicalPortNumbers) {
        int lagLogicalPortNumber = LagLogicalPort.generateLogicalPortNumber(physicalPortNumbers, lagPortOffset);

        transactionManager.doInTransaction(() -> {
            Optional<LagLogicalPort> createdLag = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                    switchId, lagLogicalPortNumber);
            if (createdLag.isPresent()) {
                // physical ports also will be removed by cascade delete operation
                lagLogicalPortRepository.remove(createdLag.get());
            } else {
                // if lag port wasn't found we will try to remove physical ports
                for (Integer portNumber : physicalPortNumbers) {
                    physicalPortRepository.findBySwitchIdAndPortNumber(switchId, portNumber)
                            .ifPresent(physicalPortRepository::remove);
                }
            }
        });
    }

    @Override
    public void validatePhysicalPorts(SwitchId switchId, List<Integer> physicalPortNumbers,
                                      Set<SwitchFeature> features) {
        if (physicalPortNumbers == null || physicalPortNumbers.isEmpty()) {
            throw new InvalidDataException("Physical ports list is empty");
        }

        for (Integer portNumber : physicalPortNumbers) {
            if (portNumber <= 0) {
                throw new InvalidDataException(format("Physical port number can't be negative: %d", portNumber));
            }
            if (features.contains(BFD) && portNumber >= bfdPortOffset && portNumber <= bfdPortMaxNumber) {
                throw new InvalidDataException(
                        format("Physical port number %d intersects with BFD port range [%d, %d]", portNumber,
                                bfdPortOffset, bfdPortMaxNumber));
            }
            if (portNumber >= lagPortOffset) {
                throw new InvalidDataException(
                        format("Physical port number %d can't be greater than LAG port offset %d.",
                                portNumber, lagPortOffset));
            }
        }

        Set<Integer> existingPhysicalPorts = physicalPortRepository.findPortNumbersBySwitchId(switchId);
        SetView<Integer> portsIntersection = Sets.intersection(
                existingPhysicalPorts, new HashSet<>(physicalPortNumbers));

        if (!portsIntersection.isEmpty()) {
            throw new InvalidDataException(
                    format("Physical ports %s on switch %s already occupied by other LAG group(s).",
                            portsIntersection, switchId));
        }
    }

    @Override
    public String getSwitchIpAddress(Switch sw) {
        if (!sw.getFeatures().contains(LAG)) {
            throw new InvalidDataException(format("Switch %s doesn't support LAG.", sw.getSwitchId()));
        }

        return Optional.ofNullable(sw.getSocketAddress()).map(IpSocketAddress::getAddress).orElseThrow(
                () -> new InconsistentDataException(
                        format("Switch %s has invalid IP address %s", sw, sw.getSocketAddress())));
    }

    @Override
    public Switch getSwitch(SwitchId switchId) {
        return switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }
}
