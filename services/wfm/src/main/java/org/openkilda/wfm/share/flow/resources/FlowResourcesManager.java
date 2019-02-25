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
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.util.UUID;

@Slf4j
public class FlowResourcesManager {
    private static final int MAX_ALLOCATION_ATTEMPTS = 5;

    private final TransactionManager transactionManager;

    private final CookiePool cookiePool;
    private final MeterPool meterPool;
    private final TransitVlanPool transitVlanPool;

    public FlowResourcesManager(PersistenceManager persistenceManager, FlowResourcesConfig config) {
        transactionManager = persistenceManager.getTransactionManager();

        this.cookiePool = new CookiePool(persistenceManager, config.getMinFlowCookie(), config.getMaxFlowCookie());
        this.meterPool = new MeterPool(persistenceManager,
                new MeterId(config.getMinFlowMeterId()), new MeterId(config.getMaxFlowMeterId()));
        this.transitVlanPool = new TransitVlanPool(persistenceManager,
                config.getMinFlowTransitVlan(), config.getMaxFlowTransitVlan());
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
                            flowResources.forwardMeter(meterPool.allocateMeter(
                                    flow.getSrcSwitch().getSwitchId(), flow.getFlowId(), forwardPathId));
                            flowResources.reverseMeter(meterPool.allocateMeter(
                                    flow.getDestSwitch().getSwitchId(), flow.getFlowId(), reversePathId));
                        }

                        if (!flow.isOneSwitchFlow()) {
                            flowResources.forwardTransitVlan(
                                    transitVlanPool.allocateVlan(flow.getFlowId(), forwardPathId));
                            flowResources.reverseTransitVlan(
                                    transitVlanPool.allocateVlan(flow.getFlowId(), reversePathId));
                        }

                        return flowResources.build();
                    }));
        } catch (ConstraintViolationException | ResourceNotAvailableException ex) {
            throw new ResourceAllocationException("Unable to allocate resources", ex);
        }
    }

    private PathId generatePathId(String flowId) {
        return new PathId(format("%s_%s", flowId, UUID.randomUUID()));
    }

    /**
     * Deallocate the flow resources.
     */
    public void deallocateFlow(PathId forwardPathId, PathId reversePathId) {
        log.debug("Deallocate flow resources for {}/{}.", forwardPathId, reversePathId);

        transactionManager.doInTransaction(() -> {
            cookiePool.deallocateCookie(forwardPathId, reversePathId);
            meterPool.deallocateMeter(forwardPathId);
            meterPool.deallocateMeter(reversePathId);
            transitVlanPool.deallocateVlan(forwardPathId);
            transitVlanPool.deallocateVlan(reversePathId);
        });
    }
}
