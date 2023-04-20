/* Copyright 2023 Telstra Open Source
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

package org.openkilda.pce;

import static com.google.common.collect.Sets.newHashSet;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslImmutableView;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A factory for {@link AvailableNetwork} instances.
 */
@Slf4j
public class AvailableNetworkFactory {
    private final PathComputerConfig config;
    private final IslRepository islRepository;
    private final FlowPathRepository flowPathRepository;

    public AvailableNetworkFactory(PathComputerConfig config, RepositoryFactory repositoryFactory) {
        this.config = config;
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
    }

    /**
     * Gets a {@link AvailableNetwork} for a flow.
     *
     * @param flow the flow, for which {@link AvailableNetwork} is constructing.
     * @param reusePathsResources reuse resources already allocated by {@param reusePathsResources} paths.
     * @return {@link AvailableNetwork} instance.
     */
    public AvailableNetwork getAvailableNetwork(Flow flow, Collection<PathId> reusePathsResources)
            throws RecoverableException {
        return getAvailableNetwork(new FlowParameters(flow), reusePathsResources);
    }

    /**
     * Gets a {@link AvailableNetwork} for a ha-flow.
     *
     * @param haFlow the ha-flow, for which {@link AvailableNetwork} is constructing.
     * @param reusePathsResources reuse resources already allocated by {@param reusePathsResources} paths.
     * @return {@link AvailableNetwork} instance.
     */
    public AvailableNetwork getAvailableNetwork(HaFlow haFlow, Collection<PathId> reusePathsResources)
            throws RecoverableException {
        return getAvailableNetwork(new FlowParameters(haFlow), reusePathsResources);
    }

    private AvailableNetwork getAvailableNetwork(FlowParameters parameters, Collection<PathId> reusePathsResources)
            throws RecoverableException {
        BuildStrategy buildStrategy = BuildStrategy.from(config.getNetworkStrategy());
        AvailableNetwork network = new AvailableNetwork();
        try {
            // Reads all active links from the database and creates representation of the network.
            getAvailableIsls(buildStrategy, parameters).forEach(link -> addIslAsEdge(link, network));
            if (!parameters.isIgnoreBandwidth()) {
                Set<PathId> reusePaths = new HashSet<>(reusePathsResources);
                reusePaths.addAll(findSharedBandwidthPathIds(parameters));
                reuseResources(reusePaths, parameters, network);
            }
        } catch (PersistenceException e) {
            throw new RecoverableException("An error from the database", e);
        }

        if (parameters.getDiverseGroupId() != null) {
            log.info("Filling AvailableNetwork diverse weights for group with id {}", parameters.getDiverseGroupId());
            fillDiverseWeights(parameters, reusePathsResources, network);
        }

        if (needToFillAffinityWeights(parameters)) {
            log.info("Filling AvailableNetwork affinity weights for group with id {}", parameters.getAffinityGroupId());
            fillAffinityWeights(parameters, network);
        }

        return network;
    }

    private Set<PathId> findSharedBandwidthPathIds(FlowParameters parameters) {
        String sharedBandwidthGroupId = null;
        if (parameters.isCommonFlow() && parameters.getYFlowId() != null) {
            sharedBandwidthGroupId = parameters.getYFlowId();
        } else if (parameters.isHaFlow() && parameters.getHaFlowId() != null) {
            sharedBandwidthGroupId = parameters.getHaFlowId();
        }
        Set<PathId> result = new HashSet<>();
        if (sharedBandwidthGroupId != null) {
            result.addAll(flowPathRepository.findPathIdsBySharedBandwidthGroupId(sharedBandwidthGroupId));
        }
        return result;
    }

    private void fillDiverseWeights(
            FlowParameters parameters, Collection<PathId> reusePathsResources, AvailableNetwork network) {
        Collection<PathId> flowPaths = flowPathRepository.findPathIdsByFlowDiverseGroupId(
                parameters.getDiverseGroupId());
        if (!reusePathsResources.isEmpty()) {
            flowPaths = flowPaths.stream()
                    .filter(s -> !reusePathsResources.contains(s))
                    .collect(Collectors.toList());
        }

        Set<PathId> affinityPathIds =
                new HashSet<>(flowPathRepository.findPathIdsByFlowAffinityGroupId(parameters.getAffinityGroupId()));
        flowPaths.forEach(pathId ->
                flowPathRepository.findById(pathId)
                        .filter(flowPath -> isNeedToAddDiversePenalties(flowPath, affinityPathIds, parameters))
                        .ifPresent(flowPath -> {
                            network.processDiversityGroupForSingleSwitchFlow(flowPath);
                            network.processDiversitySegments(flowPath.getSegments(),
                                    parameters.getTerminatingSwitchIds());
                            network.processDiversitySegmentsWithPop(flowPath.getSegments());
                        }));
    }

    private boolean isNeedToAddDiversePenalties(FlowPath path, Set<PathId> affinityPathIds, FlowParameters parameters) {
        if (parameters.isCommonFlow() && Objects.equals(path.getFlowId(), parameters.getFlowId())) {
            return true; // it is a diverse group for a protected path of a common flow
        }
        if (parameters.isHaFlow() && Objects.equals(path.getHaFlowId(), parameters.getHaFlowId())) {
            return true; // it is a diverse group for a protected path of an ha flow
        }
        return !affinityPathIds.contains(path.getPathId()); // affinity group priority is higher then diversity
    }

    /**
     * The main flow id is set as the affinity group id.
     * All flow paths in this group are built along the path of the main flow.
     * The main flow should not take into account the rest of the flows in this affinity group.
     */
    private static boolean needToFillAffinityWeights(FlowParameters parameters) {
        if (parameters.getAffinityGroupId() == null) {
            return false;
        }
        if (parameters.isCommonFlow() && parameters.getAffinityGroupId().equals(parameters.getFlowId())) {
            return false;
        }
        return !(parameters.isHaFlow() && parameters.getAffinityGroupId().equals(parameters.getHaFlowId()));
    }

    private void fillAffinityWeights(FlowParameters parameters, AvailableNetwork network) {
        flowPathRepository.findByFlowId(parameters.getAffinityGroupId()).stream()
                .filter(flowPath -> !flowPath.isProtected())
                .filter(FlowPath::isForward)
                .map(FlowPath::getSegments)
                .forEach(network::processAffinitySegments);
    }

    private void reuseResources(
            Collection<PathId> reusePathsResources, FlowParameters parameters, AvailableNetwork network) {
        reusePathsResources.stream()
                .filter(pathId -> flowPathRepository.findById(pathId)
                        .map(path -> !path.isIgnoreBandwidth())
                        .orElse(false))
                .forEach(pathId -> {
                    // ISLs occupied by the flow (take the bandwidth already occupied by the flow into account).
                    islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                                    pathId, parameters.getBandwidth(), parameters.getEncapsulationType())
                            .forEach(link -> addIslAsEdge(link, network));
                });
    }

    private Collection<IslImmutableView> getAvailableIsls(BuildStrategy buildStrategy, FlowParameters parameters) {
        if (buildStrategy == BuildStrategy.COST) {
            Collection<IslImmutableView> isls;
            if (parameters.isIgnoreBandwidth()) {
                isls = islRepository.findActiveByEncapsulationType(parameters.getEncapsulationType());
            } else {
                isls = islRepository.findActiveByBandwidthAndEncapsulationType(parameters.getBandwidth(),
                        parameters.getEncapsulationType());
            }
            validateIslsCost(isls);
            return isls;
        } else if (buildStrategy == BuildStrategy.SYMMETRIC_COST) {
            Collection<IslImmutableView> isls;
            if (parameters.isIgnoreBandwidth()) {
                isls = islRepository.findActiveByEncapsulationType(parameters.getEncapsulationType());
            } else {
                isls = islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(parameters.getBandwidth(),
                        parameters.getEncapsulationType());
            }
            validateIslsCost(isls);
            return isls;
        } else {
            throw new UnsupportedOperationException(String.format("Unsupported buildStrategy type %s", buildStrategy));
        }
    }

    private void validateIslsCost(Collection<IslImmutableView> isls) {
        List<String> messages = new ArrayList<>();

        for (IslImmutableView isl : isls) {
            if (isl.getCost() < 0) {
                messages.add(String.format("(%s_%d ===> %s_%d cost: %d)",
                        isl.getSrcSwitchId(), isl.getSrcPort(), isl.getDestSwitchId(),
                        isl.getDestPort(), isl.getCost()));
            }
        }
        if (!messages.isEmpty()) {
            log.error("Invalid network state. Following ISLs have negative costs: {}", String.join(", ", messages));
        }
    }

    private void addIslAsEdge(IslImmutableView isl, AvailableNetwork network) {
        Node srcSwitch = network.getOrAddNode(isl.getSrcSwitchId(), isl.getSrcPop());
        Node dstSwitch = network.getOrAddNode(isl.getDestSwitchId(), isl.getDestPop());

        Edge edge = Edge.builder()
                .srcSwitch(srcSwitch)
                .srcPort(isl.getSrcPort())
                .destSwitch(dstSwitch)
                .destPort(isl.getDestPort())
                .cost(isl.getCost())
                .latency(isl.getLatency())
                .underMaintenance(isl.isUnderMaintenance())
                .unstable(isl.isUnstable())
                .availableBandwidth(isl.getAvailableBandwidth())
                .build();
        network.addEdge(edge);
    }

    public enum BuildStrategy {
        /**
         * WeightStrategy based on cost of links.
         */
        COST,

        /**
         * Based on cost with always equal forward and reverse paths.
         */
        SYMMETRIC_COST;

        private static BuildStrategy from(String strategy) {
            try {
                return valueOf(strategy.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("BuildStrategy %s is not supported", strategy));
            }
        }
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class FlowParameters {
        public FlowParameters(Flow flow) {
            this(flow.isIgnoreBandwidth(), flow.getBandwidth(), flow.getEncapsulationType(), flow.getFlowId(), null,
                    flow.getYFlowId(), flow.getDiverseGroupId(), flow.getAffinityGroupId(),
                    newHashSet(flow.getSrcSwitchId(), flow.getDestSwitchId()), true, false);
        }

        public FlowParameters(HaFlow haFlow) {
            this(haFlow.isIgnoreBandwidth(), haFlow.getMaximumBandwidth(), haFlow.getEncapsulationType(), null,
                    haFlow.getHaFlowId(), null, haFlow.getDiverseGroupId(), haFlow.getAffinityGroupId(),
                    getSwitchIds(haFlow), false, true);
        }

        boolean ignoreBandwidth;
        long bandwidth;
        FlowEncapsulationType encapsulationType;
        String flowId;
        String haFlowId;
        String yFlowId;
        String diverseGroupId;
        String affinityGroupId;
        @NonNull
        Set<SwitchId> terminatingSwitchIds;

        boolean commonFlow;
        boolean haFlow;

        private static Set<SwitchId> getSwitchIds(HaFlow haFlow) {
            Set<SwitchId> result = haFlow.getHaSubFlows().stream()
                    .map(HaSubFlow::getEndpointSwitchId)
                    .collect(Collectors.toSet());
            result.add(haFlow.getSharedSwitchId());
            return result;
        }
    }
}
