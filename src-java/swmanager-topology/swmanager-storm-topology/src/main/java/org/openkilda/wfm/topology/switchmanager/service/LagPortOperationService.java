/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.service;

import static java.lang.String.format;
import static org.openkilda.model.SwitchFeature.BFD;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Isl;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PathId;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.utils.PoolManager;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.InvalidDataException;
import org.openkilda.wfm.topology.switchmanager.error.LagPortNotFoundException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.map.LRUMap;

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

@Slf4j
public class LagPortOperationService {
    private final TransactionManager transactionManager;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final LagLogicalPortRepository lagLogicalPortRepository;
    private final PhysicalPortRepository physicalPortRepository;
    private final IslRepository islRepository;
    private final FlowRepository flowRepository;
    private final FlowMirrorPathRepository flowMirrorPathRepository;

    private final LagPortOperationConfig config;

    private final LRUMap<SwitchId, PoolManager<LagLogicalPort>> portNumberPool;

    public LagPortOperationService(LagPortOperationConfig config) {
        this.transactionManager = config.getTransactionManager();

        @NonNull RepositoryFactory repositoryFactory = config.getRepositoryFactory();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        this.physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();

        this.config = config;

        portNumberPool = new LRUMap<>(config.getPoolCacheSize());
    }

    /**
     * Create LAG logical port.
     */
    public int createLagPort(SwitchId switchId, Set<Integer> physicalPortNumbers) {
        if (physicalPortNumbers == null || physicalPortNumbers.isEmpty()) {
            throw new InvalidDataException("Physical ports list is empty");
        }

        LagLogicalPort port = transactionManager.doInTransaction(
                newCreateRetryPolicy(switchId), () -> createTransaction(switchId, physicalPortNumbers));
        return port.getLogicalPortNumber();
    }

    /**
     * Delete LAG logical port.
     */
    public LagLogicalPort removeLagPort(SwitchId switchId, int logicalPortNumber) {
        return transactionManager.doInTransaction(
                newDeleteRetryPolicy(switchId), () -> deleteTransaction(switchId, logicalPortNumber));
    }

    /**
     * Verify that LAG logical port can be removed (it exists and do not in use by any flow).
     */
    public LagLogicalPort ensureDeleteIsPossible(SwitchId switchId, int logicalPortNumber) {
        Switch sw = querySwitch(switchId); // locate switch first to produce correct error if switch is missing
        Optional<LagLogicalPort> port = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                switchId, logicalPortNumber);
        if (!port.isPresent()) {
            throw new LagPortNotFoundException(switchId, logicalPortNumber);
        }

        List<String> occupiedBy = flowRepository.findByEndpoint(switchId, logicalPortNumber).stream()
                .map(Flow::getFlowId)
                .collect(Collectors.toList());
        if (!occupiedBy.isEmpty()) {
            throw new InvalidDataException(format("Couldn't delete LAG port '%d' from switch %s because flows '%s' "
                    + "use it as endpoint", logicalPortNumber, switchId, occupiedBy));
        }

        if (!isSwitchLagCapable(sw)) {
            log.error(
                    "Processing request for remove existing LAG logical port #{} on switch {} without LAG support",
                    logicalPortNumber, switchId);
        }

        return port.get();
    }

    private void validatePhysicalPort(SwitchId switchId, Set<SwitchFeature> features, Integer portNumber)
            throws InvalidDataException {
        if (portNumber == null || portNumber <= 0) {
            throw new InvalidDataException(format("Invalid physical port number %s. It can't be null or negative.",
                    portNumber));
        }

        int bfdPortOffset = config.getBfdPortOffset();
        int bfdPortMaxNumber = config.getBfdPortMaxNumber();
        if (features.contains(BFD) && portNumber >= bfdPortOffset && portNumber <= bfdPortMaxNumber) {
            throw new InvalidDataException(
                    format("Physical port number %d intersects with BFD port range [%d, %d]", portNumber,
                            bfdPortOffset, bfdPortMaxNumber));
        }

        long lagPortOffset = config.getPoolConfig().getIdMinimum();
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

    /**
     * Fetch and decode switch IP address from DB.
     */
    public String getSwitchIpAddress(SwitchId switchId) throws InvalidDataException, InconsistentDataException {
        Switch sw = querySwitch(switchId);
        return Optional.ofNullable(sw.getSocketAddress()).map(IpSocketAddress::getAddress).orElseThrow(
                () -> new InconsistentDataException(
                        format("Switch %s has invalid IP address %s", sw, sw.getSocketAddress())));
    }

    private LagLogicalPort createTransaction(SwitchId switchId, Set<Integer> targetPorts)  {
        Switch sw = querySwitch(switchId); // locate switch first to produce correct error if switch is missing
        if (! isSwitchLagCapable(sw)) {
            throw new InvalidDataException(format("Switch %s doesn't support LAG.", sw.getSwitchId()));
        }

        Set<SwitchFeature> features = sw.getFeatures();
        for (Integer portNumber : targetPorts) {
            validatePhysicalPort(switchId, features, portNumber);
        }

        ensureNoLagCollisions(switchId, targetPorts);

        LagLogicalPort port = queryPoolManager(switchId).allocate();
        port.setPhysicalPorts(
                targetPorts.stream()
                        .map(portNumber -> new PhysicalPort(switchId, portNumber, port))
                        .collect(Collectors.toList()));

        log.info("Adding new LAG logical port entry into DB: {}", port);
        lagLogicalPortRepository.add(port);
        return port;
    }

    private LagLogicalPort deleteTransaction(SwitchId switchId, int logicalPortNumber) {
        LagLogicalPort port = ensureDeleteIsPossible(switchId, logicalPortNumber);
        log.info("Removing LAG logical port entry into DB: {}", port);
        lagLogicalPortRepository.remove(port);
        return port;
    }

    private boolean isSwitchLagCapable(Switch sw) {
        return sw.getFeatures().contains(SwitchFeature.LAG);
    }

    private void ensureNoLagCollisions(SwitchId switchId, Set<Integer> targetPorts) {
        Set<Integer> occupiedPorts = physicalPortRepository.findPortNumbersBySwitchId(switchId);
        // FIXME(surabujin): we are unreasonably supposing that all physical port objects in DB related to LAGs
        SetView<Integer> intersection = Sets.intersection(occupiedPorts, new HashSet<>(targetPorts));

        if (! intersection.isEmpty()) {
            throw new InvalidDataException(
                    format("Physical ports [%s] on switch %s already occupied by other LAG group(s).",
                            intersection.stream().sorted()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(", ")), switchId));
        }
    }

    private PoolManager<LagLogicalPort> queryPoolManager(SwitchId switchId) {
        return portNumberPool.computeIfAbsent(switchId, this::newPoolManager);
    }

    private Switch querySwitch(SwitchId switchId) throws SwitchNotFoundException {
        return switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
    }

    private PoolManager<LagLogicalPort> newPoolManager(SwitchId switchId) {
        LagPortPoolEntityAdapter adapter = new LagPortPoolEntityAdapter(config, lagLogicalPortRepository, switchId);
        return new PoolManager<>(config.getPoolConfig(), adapter);
    }

    private RetryPolicy<LagLogicalPort> newCreateRetryPolicy(SwitchId switchId) {
        return newRetryPolicy(switchId, "create");
    }

    private RetryPolicy<LagLogicalPort> newDeleteRetryPolicy(SwitchId switchId) {
        return newRetryPolicy(switchId, "delete");
    }

    private RetryPolicy<LagLogicalPort> newRetryPolicy(SwitchId switchId, String action) {
        return transactionManager.<LagLogicalPort>getDefaultRetryPolicy()
                .handle(ConstraintViolationException.class)
                .onRetry(
                        e -> log.warn(
                                "Unable to {} LAG logical port DB record for switch {}: {}. Retrying #{}...",
                                action, switchId, e.getLastFailure().getMessage(), e.getAttemptCount()))
                .onRetriesExceeded(
                        e -> log.error(
                                "Failed to {} LAG logical port DB record for switch {}: {}",
                                action, switchId, e.getFailure().getMessage(), e.getFailure()));
    }
}
