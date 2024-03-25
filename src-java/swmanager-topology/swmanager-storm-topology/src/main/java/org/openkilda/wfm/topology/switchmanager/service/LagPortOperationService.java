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

import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Isl;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PathId;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.Port;
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
import org.openkilda.persistence.repositories.PortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.share.utils.PoolManager;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.InvalidDataException;
import org.openkilda.wfm.topology.switchmanager.error.LagPortNotFoundException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.model.LagRollbackData;
import org.openkilda.wfm.topology.switchmanager.model.LagRollbackData.LagRollbackDataBuilder;
import org.openkilda.wfm.topology.switchmanager.service.configs.LagPortOperationConfig;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import dev.failsafe.RetryPolicy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private final PortRepository portRepository;
    private final RuleManager ruleManager;

    private final LagPortOperationConfig config;

    private final LRUMap<SwitchId, PoolManager<LagLogicalPort>> portNumberPool;



    public LagPortOperationService(LagPortOperationConfig config, RuleManager ruleManager) {
        this.transactionManager = config.getTransactionManager();
        this.ruleManager = ruleManager;

        @NonNull RepositoryFactory repositoryFactory = config.getRepositoryFactory();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        this.lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        this.physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        this.portRepository = repositoryFactory.createPortRepository();

        this.config = config;

        portNumberPool = new LRUMap<>(config.getPoolCacheSize());
    }

    /**
     * Create LAG logical port.
     */
    public int createLagPort(SwitchId switchId, Set<Integer> targetPorts, boolean lacpReply) {
        verifyTargetPortsInput(targetPorts);
        LagLogicalPort port = transactionManager.doInTransaction(
                newCreateRetryPolicy(switchId), () -> createTransaction(switchId, targetPorts, lacpReply));
        return port.getLogicalPortNumber();
    }

    /**
     * Update LAG logical port.
     */
    public LagRollbackData updateLagPort(
            SwitchId switchId, int logicalPortNumber, Set<Integer> targetPorts, boolean lacpReply) {
        verifyTargetPortsInput(targetPorts);
        LagRollbackDataBuilder rollbackDataBuilder = LagRollbackData.builder();
        transactionManager.doInTransaction(
                newUpdateRetryPolicy(switchId),
                () -> {
                    LagRollbackData data = updateTransaction(switchId, logicalPortNumber, targetPorts, lacpReply);
                    rollbackDataBuilder.physicalPorts(data.getPhysicalPorts());
                    rollbackDataBuilder.lacpReply(data.isLacpReply());
                });
        return rollbackDataBuilder.build();
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
        LagLogicalPort port = queryLagPort(switchId, logicalPortNumber);

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

        return port;
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

    /**
     * Builds LACP commands witch will be sent to speaker.
     */
    public List<OfCommand> buildLacpSpeakerCommands(SwitchId switchId, int port) {
        DataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .switchIds(Collections.singleton(switchId))
                .pathIds(Collections.emptySet())
                .persistenceManager(config.getPersistenceManager())
                .build();
        return OfCommand.toOfCommands(ruleManager.buildLacpRules(switchId, port, dataAdapter));
    }

    private void verifyTargetPortsInput(Set<Integer> targetPorts) {
        if (targetPorts == null || targetPorts.isEmpty()) {
            throw new InvalidDataException("Physical ports list is empty");
        }
    }

    private LagLogicalPort createTransaction(SwitchId switchId, Set<Integer> targetPorts, boolean lacpReply) {
        Switch sw = querySwitch(switchId); // locate switch first to produce correct error if switch is missing
        ensureLagDataValid(sw, targetPorts);
        ensureNoLagCollisions(sw.getSwitchId(), targetPorts);

        LagLogicalPort port = queryPoolManager(switchId).allocate(entityId -> newLagLogicalPort(
                switchId, entityId, lacpReply));
        replacePhysicalPorts(port, switchId, targetPorts);

        log.info("Adding new LAG logical port entry into DB: {}", port);
        lagLogicalPortRepository.add(port);
        return port;
    }

    private LagRollbackData updateTransaction(
            SwitchId switchId, int logicalPortNumber, Set<Integer> targetPorts, boolean lacpReply) {
        Switch sw = querySwitch(switchId);
        ensureLagDataValid(sw, targetPorts);
        ensureNoLagCollisions(switchId, targetPorts, logicalPortNumber);
        ensureTargetPortsBandwidthValid(sw, targetPorts, logicalPortNumber);

        LagLogicalPort port = queryLagPort(sw.getSwitchId(), logicalPortNumber);
        LagRollbackData rollbackData = new LagRollbackData(port.getPhysicalPorts().stream()
                .map(PhysicalPort::getPortNumber)
                .collect(Collectors.toSet()), port.isLacpReply());
        log.info(
                "Updating LAG logical port #{} on {} entry desired target ports set {}, current target ports set {}. "
                        + "Desired LACP reply {}, current LACP reply {}",
                logicalPortNumber, switchId,
                formatPortNumbersSet(targetPorts), formatPortNumbersSet(rollbackData.getPhysicalPorts()),
                lacpReply, rollbackData.isLacpReply());
        replacePhysicalPorts(port, switchId, targetPorts);
        port.setLacpReply(lacpReply);

        return rollbackData;
    }

    private LagLogicalPort deleteTransaction(SwitchId switchId, int logicalPortNumber) {
        LagLogicalPort port = ensureDeleteIsPossible(switchId, logicalPortNumber);
        log.info("Removing LAG logical port entry from DB: {}", port);
        lagLogicalPortRepository.remove(port);
        return port;
    }

    private void ensureLagDataValid(Switch sw, Set<Integer> targetPorts) {
        ensureSwitchIsLagCapable(sw);
        ensureTargetPortsValid(sw, targetPorts);
    }

    private void ensureSwitchIsLagCapable(Switch sw) {
        if (!isSwitchLagCapable(sw)) {
            throw new InvalidDataException(format("Switch %s doesn't support LAG.", sw.getSwitchId()));
        }
    }

    private boolean isSwitchLagCapable(Switch sw) {
        return sw.getFeatures().contains(SwitchFeature.LAG);
    }

    private void ensureTargetPortsValid(Switch sw, Set<Integer> targetPorts) {
        Set<SwitchFeature> features = sw.getFeatures();
        SwitchId switchId = sw.getSwitchId();
        for (Integer portNumber : targetPorts) {
            validatePhysicalPort(switchId, features, portNumber);
        }
    }

    private void ensureTargetPortsBandwidthValid(Switch sw, Set<Integer> targetPorts, int logicalPortNumber) {
        long portBandwidthSum = portRepository.getAllBySwitchId(sw.getSwitchId()).stream()
                .filter(p -> targetPorts.contains(p.getPortNo()))
                .mapToLong(Port::getCurrentSpeed)
                .sum();

        long flowBandwidthSum = flowRepository.findByEndpoint(sw.getSwitchId(), logicalPortNumber).stream()
                .filter(f -> !f.isIgnoreBandwidth())
                .mapToLong(Flow::getBandwidth)
                .sum();

        if (flowBandwidthSum > portBandwidthSum) {
            throw new InvalidDataException(format("Not enough bandwidth for LAG port %s.", logicalPortNumber));
        }
    }

    private void ensureNoLagCollisions(SwitchId switchId, Set<Integer> targetPorts) {
        ensureNoLagCollisions(switchId, targetPorts, null);
    }

    private void ensureNoLagCollisions(SwitchId switchId, Set<Integer> targetPorts, Integer excludeLogicalPort) {
        // FIXME(surabujin): we are unreasonably supposing that all physical port objects in DB related to LAGs
        Collection<PhysicalPort> occupiedPorts = physicalPortRepository.findBySwitchId(switchId);
        Set<Integer> deniedTargets = new HashSet<>();
        for (PhysicalPort entry : occupiedPorts) {
            LagLogicalPort partOf = entry.getLagLogicalPort();
            if (excludeLogicalPort != null && partOf != null && excludeLogicalPort == partOf.getLogicalPortNumber()) {
                log.debug(
                        "Exclude port #{} on {} from collision list, because it is part of {} LAG logical port",
                        entry.getPortNumber(), switchId, excludeLogicalPort);
                continue;
            }
            deniedTargets.add(entry.getPortNumber());
        }

        SetView<Integer> intersection = Sets.intersection(deniedTargets, new HashSet<>(targetPorts));

        if (!intersection.isEmpty()) {
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

    private LagLogicalPort newLagLogicalPort(SwitchId switchId, long portNumber, boolean lacpReply) {
        return new LagLogicalPort(switchId, (int) portNumber, Collections.<Integer>emptyList(), lacpReply);
    }

    private LagLogicalPort queryLagPort(SwitchId switchId, int logicalPortNumber) {
        Optional<LagLogicalPort> port = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                switchId, logicalPortNumber);
        return port.orElseThrow(() -> new LagPortNotFoundException(switchId, logicalPortNumber));
    }

    private void replacePhysicalPorts(LagLogicalPort port, SwitchId switchId, Set<Integer> targetPorts) {
        port.setPhysicalPorts(targetPorts.stream()
                .map(portNumber -> new PhysicalPort(switchId, portNumber, port))
                .collect(Collectors.toList()));
    }

    private PoolManager<LagLogicalPort> newPoolManager(SwitchId switchId) {
        LagPortPoolEntityAdapter adapter = new LagPortPoolEntityAdapter(config, lagLogicalPortRepository, switchId);
        return new PoolManager<>(config.getPoolConfig(), adapter);
    }

    private RetryPolicy<LagLogicalPort> newCreateRetryPolicy(SwitchId switchId) {
        return newRetryPolicy(switchId, "create");
    }

    private RetryPolicy<LagLogicalPort> newUpdateRetryPolicy(SwitchId switchId) {
        return newRetryPolicy(switchId, "update");
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
                                action, switchId, e.getLastException().getMessage(), e.getAttemptCount()))
                .onRetriesExceeded(
                        e -> log.error(
                                "Failed to {} LAG logical port DB record for switch {}: {}",
                                action, switchId, e.getException().getMessage(), e.getException()))
                .build();
    }

    private static String formatPortNumbersSet(Set<Integer> ports) {
        return ports.stream()
                .sorted()
                .map(Objects::toString)
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
