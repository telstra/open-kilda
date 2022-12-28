/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanPool;
import org.openkilda.wfm.share.flow.resources.vxlan.VxlanPool;
import org.openkilda.wfm.share.utils.PoolManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
public class FlowResourcesManager {
    private static final int POOL_SIZE = 100;

    private final TransactionManager transactionManager;
    private final FlowMeterRepository flowMeterRepository;

    private final CookiePool cookiePool;
    private final MirrorGroupIdPool mirrorGroupIdPool;

    private final LRUMap<SwitchId, PoolManager<FlowMeter>> meterIdPools;
    private final PoolManager.PoolConfig meterIdPoolConfig;

    private final Map<FlowEncapsulationType, EncapsulationResourcesProvider<? extends EncapsulationResources>>
            encapsulationResourcesProviders;

    public FlowResourcesManager(PersistenceManager persistenceManager, FlowResourcesConfig config) {
        transactionManager = persistenceManager.getTransactionManager();

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowMeterRepository = repositoryFactory.createFlowMeterRepository();

        this.cookiePool = new CookiePool(persistenceManager, config.getMinFlowCookie(), config.getMaxFlowCookie(),
                POOL_SIZE);

        meterIdPools = new LRUMap<>(config.getPoolsCacheSizeMeterId());
        meterIdPoolConfig = new PoolManager.PoolConfig(
                config.getMinFlowMeterId(), config.getMaxFlowMeterId(), config.getPoolChunksCountMeterId());

        this.mirrorGroupIdPool = new MirrorGroupIdPool(persistenceManager,
                new GroupId(config.getMinGroupId()), new GroupId(config.getMaxGroupId()), POOL_SIZE);

        encapsulationResourcesProviders = ImmutableMap.<FlowEncapsulationType,
                        EncapsulationResourcesProvider<? extends EncapsulationResources>>builder()
                .put(FlowEncapsulationType.TRANSIT_VLAN, new TransitVlanPool(persistenceManager,
                        config.getMinFlowTransitVlan(), config.getMaxFlowTransitVlan(), POOL_SIZE))
                .put(FlowEncapsulationType.VXLAN, new VxlanPool(persistenceManager,
                        config.getMinFlowVxlan(), config.getMaxFlowVxlan(), POOL_SIZE))
                .build();
    }

    /**
     * Try to allocate resources for the flow paths. The method doesn't initialize a transaction.
     * So it requires external transaction to cover allocation failures.
     * <p/>
     * Provided two flows are considered as paired (forward and reverse),
     * so some resources can be shared among them.
     */
    public FlowResources allocateFlowResources(Flow flow) throws ResourceAllocationException {
        PathId forwardPathId = generatePathId(flow.getFlowId());
        PathId reversePathId = generatePathId(flow.getFlowId());
        return allocateFlowResources(flow, forwardPathId, reversePathId);
    }

    /**
     * Try to allocate resources for the flow paths. The method doesn't initialize a transaction.
     * So it requires external transaction to cover allocation failures.
     * <p/>
     * Provided two flows are considered as paired (forward and reverse),
     * so some resources can be shared among them.
     */
    public FlowResources allocateFlowResources(Flow flow, PathId forwardPathId, PathId reversePathId)
            throws ResourceAllocationException {
        log.debug("Allocate flow resources for {}.", flow);

        try {
            return allocateResources(flow, forwardPathId, reversePathId);
        } catch (ConstraintViolationException | ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate resources", ex);
        }
    }

    @VisibleForTesting
    FlowResources allocateResources(Flow flow, PathId forwardPathId, PathId reversePathId) {
        PathResources.PathResourcesBuilder forward = PathResources.builder()
                .pathId(forwardPathId);
        PathResources.PathResourcesBuilder reverse = PathResources.builder()
                .pathId(reversePathId);

        if (flow.getBandwidth() > 0L) {
            forward.meterId(allocatePathMeter(flow.getSrcSwitchId(), flow.getFlowId(), forwardPathId));
            reverse.meterId(allocatePathMeter(flow.getDestSwitchId(), flow.getFlowId(), reversePathId));
        }

        if (!flow.isOneSwitchFlow()) {
            EncapsulationResourcesProvider<?> encapsulationResourcesProvider =
                    getEncapsulationResourcesProvider(flow.getEncapsulationType());
            forward.encapsulationResources(
                    encapsulationResourcesProvider.allocate(flow, forwardPathId, reversePathId));

            reverse.encapsulationResources(
                    encapsulationResourcesProvider.allocate(flow, reversePathId, forwardPathId));
        }

        return FlowResources.builder()
                .unmaskedCookie(cookiePool.allocate(flow.getFlowId()))
                .forward(forward.build())
                .reverse(reverse.build())
                .build();
    }

    /**
     * Try to allocate resources for the flow paths. The method doesn't initialize a transaction.
     * So it requires external transaction to cover allocation failures.
     * <p/>
     * Provided two flows are considered as paired (forward and reverse),
     * so some resources can be shared among them.
     */
    public FlowResources allocateFlowMirrorResources(
            Flow flow, String mirrorPointId, SwitchId srcSwitchId, SwitchId dstSwitchId, boolean bidirectional)
            throws ResourceAllocationException {
        log.debug("Allocate flow mirror resources for flow {} mirror point {}", flow, mirrorPointId);
        PathId forwardPathId = generateMirrorPathId(flow.getFlowId(), mirrorPointId);
        PathId reversePathId = generateMirrorPathId(flow.getFlowId(), mirrorPointId);

        try {
            PathResources.PathResourcesBuilder forward = PathResources.builder().pathId(forwardPathId);
            PathResources.PathResourcesBuilder reverse = PathResources.builder().pathId(reversePathId);

            EncapsulationResourcesProvider<?> encapsulationResourcesProvider =
                    getEncapsulationResourcesProvider(flow.getEncapsulationType());

            if (!srcSwitchId.equals(dstSwitchId)) {
                forward.encapsulationResources(
                        encapsulationResourcesProvider.allocate(flow, forwardPathId, reversePathId));
                if (bidirectional) {
                    reverse.encapsulationResources(
                            encapsulationResourcesProvider.allocate(flow, reversePathId, forwardPathId));
                }
            }

            return FlowResources.builder()
                    .unmaskedCookie(allocateCookie(flow.getFlowId()))
                    .forward(forward.build())
                    .reverse(reverse.build())
                    .build();
        } catch (ConstraintViolationException | ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate resources", ex);
        }
    }

    private EncapsulationResourcesProvider<?> getEncapsulationResourcesProvider(FlowEncapsulationType type) {
        EncapsulationResourcesProvider<?> provider = encapsulationResourcesProviders.get(type);
        if (provider == null) {
            throw new ResourceNotAvailableException(
                    format("Unsupported encapsulation type %s", type));
        }
        return provider;
    }

    public PathId generatePathId(String flowId) {
        return new PathId(format("%s_%s", flowId, UUID.randomUUID()));
    }

    public PathId generateMirrorPathId(String flowId, String mirrorPointId) {
        return new PathId(format("%s_%s_%s_mirror", flowId, mirrorPointId, UUID.randomUUID()));
    }

    /**
     * Deallocate the flow path resources.
     * <p/>
     * Shared resources are to be deallocated with no usage checks.
     */
    public void deallocatePathResources(PathId pathId, long unmaskedCookie, FlowEncapsulationType encapsulationType) {
        log.debug("Deallocate flow resources for path {}, cookie: {}.", pathId, unmaskedCookie);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocate(unmaskedCookie);
            deallocatePathMeter(pathId);

            EncapsulationResourcesProvider<? extends EncapsulationResources> encapsulationResourcesProvider =
                    getEncapsulationResourcesProvider(encapsulationType);
            encapsulationResourcesProvider.deallocate(pathId);
        });
    }

    /**
     * Deallocate the flow path resources.
     * <p/>
     * Shared resources are to be deallocated with no usage checks.
     */
    public void deallocatePathResources(FlowResources resources) {
        log.debug("Deallocate flow resources {}.", resources);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocate(resources.getUnmaskedCookie());

            Stream.of(resources.getForward(), resources.getReverse())
                    .forEach(path -> {
                        deallocatePathMeter(path.getPathId());
                        EncapsulationResources encapsulationResources = path.getEncapsulationResources();
                        if (encapsulationResources != null) {
                            getEncapsulationResourcesProvider(encapsulationResources.getEncapsulationType())
                                    .deallocate(path.getPathId());
                        }
                    });
        });
    }

    /**
     * Get allocated encapsulation resources of the flow path.
     */
    public Optional<? extends EncapsulationResources> getEncapsulationResources(
            PathId pathId, PathId oppositePathId, FlowEncapsulationType encapsulationType) {
        return getEncapsulationResourcesProvider(encapsulationType).get(pathId, oppositePathId);
    }

    /**
     * Get allocated mirror group id. The method doesn't initialize a transaction.
     * So it requires external transaction to cover allocation failures.
     */
    public MirrorGroup getAllocatedMirrorGroup(SwitchId switchId, String flowId, PathId pathId,
                                               MirrorGroupType type, MirrorDirection direction)
            throws ResourceAllocationException {
        try {
            return mirrorGroupIdPool.allocate(switchId, flowId, pathId, type, direction);
        } catch (ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate cookie", ex);
        }
    }

    /**
     * Deallocate the mirror group id resource.
     */
    public void deallocateMirrorGroup(PathId pathId, SwitchId switchId) {
        log.debug("Deallocate mirror group id on the switch: {} and path: {}", switchId, pathId);
        mirrorGroupIdPool.deallocate(pathId, switchId);
    }

    /**
     * Try to allocate cookie for the flow path. The method doesn't initialize a transaction.
     * So it requires external transaction to cover allocation failures.
     */
    public long allocateCookie(String flowId) throws ResourceAllocationException {
        try {
            return cookiePool.allocate(flowId);
        } catch (ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate cookie", ex);
        }
    }

    /**
     * Deallocate the cookie resource.
     */
    public void deallocateCookie(long unmaskedCookie) {
        log.debug("Deallocate cookie: {}.", unmaskedCookie);
        cookiePool.deallocate(unmaskedCookie);
    }

    /**
     * Try to allocate meter which is bound to a flow only.
     * The method doesn't initialize a transaction. So it requires external transaction to cover allocation failures.
     */
    public MeterId allocateMeter(String flowId, SwitchId switchId) throws ResourceAllocationException {
        try {
            FlowMeter flowMeter = newFlowMeter(switchId, flowId);
            flowMeterRepository.add(flowMeter);
            return flowMeter.getMeterId();
        } catch (ResourceNotAvailableException ex) {
            throw new ResourceAllocationException(
                    format("Unable to allocate meter for flow %s on switch %s", flowId, switchId), ex);
        }
    }

    /**
     * Deallocates a meter.
     */
    public void deallocateMeter(SwitchId switchId, MeterId meterId) {
        transactionManager.doInTransaction(
                () -> flowMeterRepository
                        .findById(switchId, meterId)
                        .ifPresent(this::deallocateFlowMeter));
    }

    /**
     * Get encapsulation map for flow paths.
     */
    public Map<PathId, EncapsulationResources> getMirrorEncapsulationMap(
            Collection<FlowMirrorPoints> mirrorPoints, FlowEncapsulationType flowEncapsulationType) {
        Map<PathId, EncapsulationResources> result = new HashMap<>();
        for (FlowMirrorPoints mirrorPoint : mirrorPoints) {
            for (FlowMirror flowMirror : mirrorPoint.getFlowMirrors()) {
                if (flowMirror.isOneSwitchMirror()) {
                    continue;
                }
                EncapsulationResources mirrorEncapsulation = getEncapsulationResources(
                                flowMirror.getForwardPathId(), flowMirror.getReversePathId(), flowEncapsulationType)
                        .orElseThrow(() -> new IllegalStateException(
                                format("Encapsulation was not found, mirror pathId: %s",
                                        flowMirror.getForwardPathId())));
                result.put(flowMirror.getForwardPathId(), mirrorEncapsulation);
            }
        }
        return result;
    }

    private MeterId allocatePathMeter(SwitchId switchId, String flowId, PathId pathId) {
        FlowMeter flowMeter = newFlowMeter(switchId, flowId, pathId);
        flowMeterRepository.add(flowMeter);
        return flowMeter.getMeterId();
    }

    private void deallocatePathMeter(PathId pathId) {
        flowMeterRepository.findByPathId(pathId)
                .ifPresent(this::deallocateFlowMeter);
    }

    private void deallocateFlowMeter(FlowMeter entity) {
        queryMeterIdPoolManager(entity.getSwitchId())
                .deallocate(() -> {
                    flowMeterRepository.remove(entity);
                    return entity.getMeterId().getValue();
                });
    }

    private FlowMeter newFlowMeter(SwitchId switchId, String flowId) {
        return newFlowMeter(switchId, flowId, null);
    }

    private FlowMeter newFlowMeter(SwitchId switchId, String flowId, PathId pathId) {
        return queryMeterIdPoolManager(switchId).allocate(entityId ->
                FlowMeter.builder()
                        .switchId(switchId)
                        .flowId(flowId)
                        .pathId(pathId)
                        .meterId(new MeterId(entityId))
                        .build());
    }

    private PoolManager<FlowMeter> queryMeterIdPoolManager(SwitchId switchId) {
        return meterIdPools.computeIfAbsent(switchId, this::newMeterIdPoolManager);
    }

    private PoolManager<FlowMeter> newMeterIdPoolManager(SwitchId switchId) {
        MeterIdPoolEntityAdapter adapter = new MeterIdPoolEntityAdapter(
                flowMeterRepository, meterIdPoolConfig, switchId);
        return new PoolManager<>(meterIdPoolConfig, adapter);
    }
}
