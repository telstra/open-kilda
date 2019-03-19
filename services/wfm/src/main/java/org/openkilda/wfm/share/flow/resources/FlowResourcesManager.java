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
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanPool;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class FlowResourcesManager {
    private static final int MAX_ALLOCATION_ATTEMPTS = 5;

    private final TransactionManager transactionManager;
    private final SwitchRepository switchRepository;

    private final CookiePool cookiePool;
    private final MeterPool meterPool;
    private final Map<FlowEncapsulationType, EncapsulationResourcesProvider> encapsulationResourcesProviders;

    public FlowResourcesManager(PersistenceManager persistenceManager, FlowResourcesConfig config) {
        transactionManager = persistenceManager.getTransactionManager();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        this.cookiePool = new CookiePool(persistenceManager, config.getMinFlowCookie(), config.getMaxFlowCookie());
        this.meterPool = new MeterPool(persistenceManager,
                new MeterId(config.getMinFlowMeterId()), new MeterId(config.getMaxFlowMeterId()));

        encapsulationResourcesProviders = ImmutableMap.<FlowEncapsulationType, EncapsulationResourcesProvider>builder()
                .put(FlowEncapsulationType.TRANSIT_VLAN, new TransitVlanPool(persistenceManager,
                        config.getMinFlowTransitVlan(), config.getMaxFlowTransitVlan()))
                .build();
    }

    /**
     * Allocate resources for the flow.
     */
    public FlowResources allocateFlowResources(Flow flow) throws ResourceAllocationException {
        log.debug("Allocate flow resources for {}.", flow);

        PathId forwardPathId = generatePathId(flow.getFlowId());
        PathId reversePathId = generatePathId(flow.getFlowId());

        try {
            return Failsafe.with(new RetryPolicy()
                    .retryOn(ConstraintViolationException.class)
                    .retryOn(ResourceNotAvailableException.class)
                    .withMaxRetries(MAX_ALLOCATION_ATTEMPTS))
                    .get(() -> transactionManager.doInTransaction(() -> {
                        PathResources.PathResourcesBuilder forward = PathResources.builder()
                                .pathId(forwardPathId);
                        PathResources.PathResourcesBuilder reverse = PathResources.builder()
                                .pathId(reversePathId);

                        if (flow.getBandwidth() > 0L) {
                            switchRepository.lockSwitches(switchRepository.reload(flow.getSrcSwitch()),
                                    switchRepository.reload(flow.getDestSwitch()));

                            forward.meterId(meterPool.allocate(
                                    flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), forwardPathId));

                            reverse.meterId(meterPool.allocate(
                                    flow.getDestSwitch().getSwitchId(), flow.getFlowId(), reversePathId));
                        }

                        if (!flow.isOneSwitchFlow()) {
                            EncapsulationResourcesProvider encapsulationResourcesProvider =
                                    getEncapsulationResourcesProvider(flow.getEncapsulationType());
                            forward.encapsulationResources(
                                    encapsulationResourcesProvider.allocate(flow, forwardPathId));

                            reverse.encapsulationResources(
                                    encapsulationResourcesProvider.allocate(flow, reversePathId));
                        }

                        return FlowResources.builder()
                                .unmaskedCookie(cookiePool.allocate(flow.getFlowId()))
                                .forward(forward.build())
                                .reverse(reverse.build())
                                .build();
                    }));
        } catch (ConstraintViolationException | ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate resources", ex);
        }
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
     * Deallocate the flow resources.
     */
    public void deallocateFlowResources(Flow flow, FlowResources flowResources) {
        log.debug("Deallocate flow resources for {}: {}.", flow, flowResources);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocate(flowResources.getUnmaskedCookie());

            switchRepository.lockSwitches(flow.getSrcSwitch(), flow.getDestSwitch());

            meterPool.deallocate(flowResources.getForward().getPathId());
            meterPool.deallocate(flowResources.getReverse().getPathId());

            EncapsulationResourcesProvider encapsulationResourcesProvider =
                    getEncapsulationResourcesProvider(flow.getEncapsulationType());
            encapsulationResourcesProvider.deallocate(flowResources.getForward().getPathId());
            encapsulationResourcesProvider.deallocate(flowResources.getReverse().getPathId());
        });
    }

    /**
     * Deallocate the flow resources.
     */
    public void deallocatePathPairResources(PathId pathId,
                                            long unmaskedCookie,
                                            FlowEncapsulationType encapsulationType) {
        log.debug("Deallocate flow resources for path {}, cookie: {}.",
                pathId, unmaskedCookie);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocate(unmaskedCookie);

            meterPool.deallocate(pathId);

            EncapsulationResourcesProvider encapsulationResourcesProvider =
                    getEncapsulationResourcesProvider(encapsulationType);
            encapsulationResourcesProvider.deallocate(pathId);
        });
    }
}
