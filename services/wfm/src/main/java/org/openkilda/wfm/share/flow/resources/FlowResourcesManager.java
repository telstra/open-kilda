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
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
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

    private final CookiePool cookiePool;
    private final MeterPool meterPool;
    private final Map<FlowEncapsulationType, EncapsulationResourcesProvider> encapsulationResourcesProviders;

    public FlowResourcesManager(PersistenceManager persistenceManager, FlowResourcesConfig config) {
        transactionManager = persistenceManager.getTransactionManager();

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
                        FlowResources.FlowResourcesBuilder flowResources = FlowResources.builder()
                                .forwardPathId(forwardPathId)
                                .reversePathId(reversePathId);
                        flowResources.unmaskedCookie(cookiePool.allocateCookie(flow.getFlowId(),
                                forwardPathId, reversePathId));

                        if (flow.getBandwidth() > 0L) {
                            FlowMeter forwardMeter = meterPool.allocateMeter(
                                    flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), forwardPathId);
                            flowResources.forwardMeterId(forwardMeter.getMeterId());
                            FlowMeter reverseMeter = meterPool.allocateMeter(
                                    flow.getDestSwitch().getSwitchId(), flow.getFlowId(), reversePathId);
                            flowResources.reverseMeterId(reverseMeter.getMeterId());
                        }

                        if (!flow.isOneSwitchFlow()) {
                            flowResources.encapsulationResources(
                                    getEncapsulationResourcesProvider(flow.getEncapsulationType())
                                            .allocate(flow, forwardPathId, reversePathId));
                        }

                        return flowResources.build();
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
    public void deallocateFlow(Flow flow, PathId forwardPathId, PathId reversePathId) {
        log.debug("Deallocate flow resources for {}/{}.", forwardPathId, reversePathId);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocateCookie(forwardPathId, reversePathId);
            meterPool.deallocateMeter(forwardPathId);
            meterPool.deallocateMeter(reversePathId);
            getEncapsulationResourcesProvider(flow.getEncapsulationType())
                    .deallocate(forwardPathId, reversePathId);
        });
    }
}
