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

import org.openkilda.model.ExclusionId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanPool;
import org.openkilda.wfm.share.flow.resources.vxlan.VxlanPool;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
public class FlowResourcesManager {
    private static final int MAX_ALLOCATION_ATTEMPTS = 5;

    private final TransactionManager transactionManager;

    private final CookiePool cookiePool;
    private final MeterPool meterPool;
    private final Map<FlowEncapsulationType, EncapsulationResourcesProvider> encapsulationResourcesProviders;

    private final ExclusionIdPool exclusionIdPool;

    public FlowResourcesManager(PersistenceManager persistenceManager, FlowResourcesConfig config) {
        transactionManager = persistenceManager.getTransactionManager();

        this.cookiePool = new CookiePool(persistenceManager, config.getMinFlowCookie(), config.getMaxFlowCookie());
        this.meterPool = new MeterPool(persistenceManager,
                new MeterId(config.getMinFlowMeterId()), new MeterId(config.getMaxFlowMeterId()));

        encapsulationResourcesProviders = ImmutableMap.<FlowEncapsulationType, EncapsulationResourcesProvider>builder()
                .put(FlowEncapsulationType.TRANSIT_VLAN, new TransitVlanPool(persistenceManager,
                        config.getMinFlowTransitVlan(), config.getMaxFlowTransitVlan()))
                .put(FlowEncapsulationType.VXLAN, new VxlanPool(persistenceManager,
                        config.getMinFlowVxlan(), config.getMaxFlowVxlan()))
                .build();

        exclusionIdPool = new ExclusionIdPool(persistenceManager);
    }

    /**
     * Allocate resources for the flow paths.
     * <p/>
     * Provided two flows are considered as paired (forward and reverse),
     * so some resources can be shared among them.
     */
    public FlowResources allocateFlowResources(Flow flow) throws ResourceAllocationException {
        log.debug("Allocate flow resources for {}.", flow);

        PathId forwardPathId = generatePathId(flow.getFlowId());
        PathId reversePathId = generatePathId(flow.getFlowId());

        try {
            return allocateResources(flow, forwardPathId, reversePathId);
        } catch (ConstraintViolationException | ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate resources", ex);
        }
    }

    /**
     * Allocate resources for the flow paths. Performs retries on internal transaction.
     * <p/>
     * Provided two flows are considered as paired (forward and reverse),
     * so some resources can be shared among them.
     */
    public FlowResources allocateFlowResourcesInTransaction(Flow flow) throws ResourceAllocationException {
        log.debug("Allocate flow resources for {}.", flow);

        PathId forwardPathId = generatePathId(flow.getFlowId());
        PathId reversePathId = generatePathId(flow.getFlowId());

        try {
            return Failsafe.with(new RetryPolicy()
                    .retryOn(ConstraintViolationException.class)
                    .retryOn(ResourceNotAvailableException.class)
                    .withMaxRetries(MAX_ALLOCATION_ATTEMPTS))
                    .onRetry(e -> log.info("Retrying resource allocation transaction finished with exception", e))
                    .get(() -> transactionManager.doInTransaction(
                            () -> allocateResources(flow, forwardPathId, reversePathId)));
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
            forward.meterId(meterPool.allocate(flow.getSrcSwitch(), flow.getFlowId(), forwardPathId));
            reverse.meterId(meterPool.allocate(flow.getDestSwitch(), flow.getFlowId(), reversePathId));
        }

        Long lldpCookie = null;
        if (flow.getDetectConnectedDevices().isSrcLldp() || flow.getDetectConnectedDevices().isDstLldp()) {
            lldpCookie = cookiePool.allocate(flow.getFlowId());

            if (flow.getDetectConnectedDevices().isSrcLldp()) {
                forward.lldpMeterId(meterPool.allocate(flow.getSrcSwitch(), flow.getFlowId(), forwardPathId));
            }
            if (flow.getDetectConnectedDevices().isDstLldp()) {
                reverse.lldpMeterId(meterPool.allocate(flow.getDestSwitch(), flow.getFlowId(), reversePathId));
            }
        }

        if (!flow.isOneSwitchFlow()) {
            EncapsulationResourcesProvider encapsulationResourcesProvider =
                    getEncapsulationResourcesProvider(flow.getEncapsulationType());
            forward.encapsulationResources(
                    encapsulationResourcesProvider.allocate(flow, forwardPathId, reversePathId));

            reverse.encapsulationResources(
                    encapsulationResourcesProvider.allocate(flow, reversePathId, forwardPathId));
        }

        return FlowResources.builder()
                .unmaskedCookie(cookiePool.allocate(flow.getFlowId()))
                .unmaskedLldpCookie(lldpCookie)
                .forward(forward.build())
                .reverse(reverse.build())
                .build();
    }

    private EncapsulationResourcesProvider getEncapsulationResourcesProvider(FlowEncapsulationType type) {
        EncapsulationResourcesProvider provider = encapsulationResourcesProviders.get(type);
        if (provider == null) {
            throw new ResourceNotAvailableException(
                    format("Unsupported encapsulation type %s", type));
        }
        return provider;
    }

    private PathId generatePathId(String flowId) {
        return new PathId(format("%s_%s", flowId, UUID.randomUUID()));
    }

    /**
     * Deallocate the flow path resources.
     * <p/>
     * Shared resources are to be deallocated with no usage checks.
     */
    public void deallocatePathResources(PathId pathId, long unmaskedCookie, Long unmaskedLldpCookie,
                                        FlowEncapsulationType encapsulationType) {
        log.debug("Deallocate flow resources for path {}, cookie: {}.", pathId, unmaskedCookie);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocate(unmaskedCookie);
            if (unmaskedLldpCookie != null) {
                cookiePool.deallocate(unmaskedLldpCookie);
            }
            meterPool.deallocate(pathId);

            EncapsulationResourcesProvider encapsulationResourcesProvider =
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
            if (resources.getUnmaskedLldpCookie() != null) {
                cookiePool.deallocate(resources.getUnmaskedLldpCookie());
            }

            meterPool.deallocate(resources.getForward().getPathId(), resources.getReverse().getPathId());

            Stream.of(resources.getForward(), resources.getReverse())
                    .forEach(path -> {
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
    public Optional<EncapsulationResources> getEncapsulationResources(PathId pathId,
                                                                      PathId oppositePathId,
                                                                      FlowEncapsulationType encapsulationType) {
        return getEncapsulationResourcesProvider(encapsulationType).get(pathId, oppositePathId);
    }

    /**
     * Allocate encapsulation resources of the flow.
     */
    public EncapsulationResources allocateEncapsulationResources(Flow flow, FlowEncapsulationType encapsulationType) {
        return getEncapsulationResourcesProvider(encapsulationType)
                .allocate(flow, flow.getForwardPathId(), flow.getReversePathId());
    }

    /**
     * Deallocate encapsulation resources of the flow.
     */
    public void deallocateEncapsulationResources(PathId pathId, FlowEncapsulationType encapsulationType) {
        getEncapsulationResourcesProvider(encapsulationType).deallocate(pathId);
    }

    /**
     * Allocate the flow exclusion id resources.
     */
    public int allocateExclusionIdResources(String flowId) {
        log.debug("Allocate exclusion id resources for the flow {}.", flowId);
        return exclusionIdPool.allocate(flowId);
    }

    /**
     * Deallocate the flow exclusion id resources.
     */
    public void deallocateExclusionIdResources(String flowId) {
        log.debug("Deallocate exclusion id resources for the flow {}.", flowId);
        exclusionIdPool.deallocate(flowId);
    }

    /**
     * Deallocate the flow exclusion id resources.
     */
    public void deallocateExclusionIdResources(String flowId, int exclusionId) {
        log.debug("Deallocate exclusion id {} for the flow {}.", exclusionId, flowId);
        exclusionIdPool.deallocate(flowId, exclusionId);
    }

    /**
     * Get the flow exclusion id resources.
     */
    public Collection<ExclusionId> getExclusionIdResources(String flowId) {
        log.debug("Get exclusion id resources for the flow {}.", flowId);
        return exclusionIdPool.get(flowId);
    }
}
