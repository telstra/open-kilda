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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Isl;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.InvalidDataException;
import org.openkilda.wfm.topology.switchmanager.error.LagPortNotFoundException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.service.LagPortOperationService;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LagPortOperationServiceImpl implements LagPortOperationService {
    private final TransactionManager transactionManager;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final LagLogicalPortRepository lagLogicalPortRepository;
    private final PhysicalPortRepository physicalPortRepository;
    private final IslRepository islRepository;
    private final FlowRepository flowRepository;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final int bfdPortOffset;
    private final int bfdPortMaxNumber;
    private final int lagPortOffset;

    public LagPortOperationServiceImpl(RepositoryFactory repositoryFactory, TransactionManager transactionManager,
                                       int bfdPortOffset, int bfdPortMaxNumber, int lagPortOffset) {
        this.transactionManager = transactionManager;
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        this.physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        this.bfdPortOffset = bfdPortOffset;
        this.bfdPortMaxNumber = bfdPortMaxNumber;
        this.lagPortOffset = lagPortOffset;
    }

    @Override
    public int createLagPort(SwitchId switchId, List<Integer> physicalPortNumbers) {
        int lagLogicalPortNumber = LagLogicalPort.generateLogicalPortNumber(physicalPortNumbers, lagPortOffset);
        LagLogicalPort lagLogicalPort = new LagLogicalPort(switchId, lagLogicalPortNumber, physicalPortNumbers);

        lagLogicalPortRepository.add(lagLogicalPort);
        return lagLogicalPortNumber;
    }

    @Override
    public Optional<LagLogicalPort> removeLagPort(SwitchId switchId, int logicalPortNumber) {
        return transactionManager.doInTransaction(() ->
                lagLogicalPortRepository.findBySwitchIdAndPortNumber(switchId, logicalPortNumber)
                        .map(port -> {
                            // physical ports also will be removed by cascade delete operation
                            lagLogicalPortRepository.remove(port);
                            return port;
                        }));
    }

    @Override
    public void validatePhysicalPorts(SwitchId switchId, List<Integer> physicalPortNumbers,
                                      Set<SwitchFeature> features) throws InvalidDataException {
        if (physicalPortNumbers == null || physicalPortNumbers.isEmpty()) {
            throw new InvalidDataException("Physical ports list is empty");
        }

        for (Integer portNumber : physicalPortNumbers) {
            validatePhysicalPort(switchId, features, portNumber);
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

    private void validatePhysicalPort(SwitchId switchId, Set<SwitchFeature> features, Integer portNumber)
            throws InvalidDataException {
        if (portNumber == null || portNumber <= 0) {
            throw new InvalidDataException(format("Invalid physical port number %s. It can't be null or negative.",
                    portNumber));
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

        Collection<Isl> isls = islRepository.findByEndpoint(switchId, portNumber);
        if (!isls.isEmpty()) {
            throw new InvalidDataException(
                    format("Physical port number %d intersects with existing ISLs %s.", portNumber, isls));
        }

        Optional<SwitchProperties> properties = switchPropertiesRepository.findBySwitchId(switchId);
        if (properties.isPresent() && Objects.equals(properties.get().getServer42Port(), portNumber)) {
            throw new InvalidDataException(
                    format("Physical port number %d on switch %s is server42 port.", portNumber, switchId));
        }

        Set<String> flowIds = flowRepository.findByEndpoint(switchId, portNumber).stream()
                .map(Flow::getFlowId).collect(Collectors.toSet());
        if (!flowIds.isEmpty()) {
            throw new InvalidDataException(format("Physical port %d already used by following flows: %s. You must "
                    + "remove these flows to be able to use the port in LAG.", portNumber, flowIds));
        }

        Collection<FlowMirrorPath> mirrorPaths = flowMirrorPathRepository
                .findByEgressSwitchIdAndPort(switchId, portNumber);
        if (!mirrorPaths.isEmpty()) {
            Map<String, List<PathId>> mirrorPathByFLowIdMap = new HashMap<>();
            for (FlowMirrorPath path : mirrorPaths) {
                String flowId = path.getFlowMirrorPoints().getFlowPath().getFlowId();
                mirrorPathByFLowIdMap.computeIfAbsent(flowId, ignore -> new ArrayList<>());
                mirrorPathByFLowIdMap.get(flowId).add(path.getPathId());
            }

            String message = mirrorPathByFLowIdMap.entrySet().stream()
                    .map(entry -> format("flow '%s': %s", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(", "));
            throw new InvalidDataException(format("Physical port %d already used as sink by following mirror points %s",
                    portNumber, message));
        }
    }

    @Override
    public String getSwitchIpAddress(Switch sw) throws InvalidDataException, InconsistentDataException {
        if (!sw.getFeatures().contains(LAG)) {
            throw new InvalidDataException(format("Switch %s doesn't support LAG.", sw.getSwitchId()));
        }

        return Optional.ofNullable(sw.getSocketAddress()).map(IpSocketAddress::getAddress).orElseThrow(
                () -> new InconsistentDataException(
                        format("Switch %s has invalid IP address %s", sw, sw.getSocketAddress())));
    }

    @Override
    public Switch getSwitch(SwitchId switchId) throws SwitchNotFoundException {
        return switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

    @Override
    public void validateLagBeforeDelete(SwitchId switchId, int logicalPortNumber)
            throws LagPortNotFoundException, InvalidDataException {
        if (!lagLogicalPortRepository.findBySwitchIdAndPortNumber(switchId, logicalPortNumber).isPresent()) {
            throw new LagPortNotFoundException(switchId, logicalPortNumber);
        }

        List<String> flowIds = flowRepository.findByEndpoint(switchId, logicalPortNumber)
                .stream().map(Flow::getFlowId).collect(Collectors.toList());
        if (!flowIds.isEmpty()) {
            throw new InvalidDataException(format("Couldn't delete LAG port '%d' from switch %s because flows '%s' "
                    + "use it as endpoint", logicalPortNumber, switchId, flowIds));
        }
    }
}
