/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.service;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.cache.ResourceCache;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

@Slf4j
@ToString
public class FlowResourcesManager {
    private final ResourceCache resourceCache;

    public FlowResourcesManager(ResourceCache resourceCache) {
        this.resourceCache = resourceCache;
    }

    /**
     * Clears the inner resource pools.
     */
    public void clear() {
        resourceCache.clear();
    }

    /**
     * Register the flow resources as used.
     *
     * @param flowPair the flow pair to register.
     */
    public void registerUsedByFlow(FlowPair flowPair) {
        Flow forward = flowPair.getForward();
        resourceCache.allocateCookie((int) (ResourceCache.FLOW_COOKIE_VALUE_MASK & forward.getCookie()));
        if (!forward.isOneSwitchFlow()) {
            // Don't allocate if one switch .. it is zero
            // .. and allocateVlanId *will* allocate if it is zero, which consumes a very
            // .. limited resource unnecessarily
            resourceCache.allocateVlanId(forward.getTransitVlan());
        }
        resourceCache.allocateMeterId(forward.getSrcSwitch().getSwitchId(), forward.getMeterId());

        Flow reverse = flowPair.getReverse();
        if (!reverse.isOneSwitchFlow()) {
            // Don't allocate if one switch .. it is zero
            // .. and allocateVlanId *will* allocate if it is zero, which consumes a very
            // .. limited resource unnecessarily
            resourceCache.allocateVlanId(reverse.getTransitVlan());
        }
        resourceCache.allocateMeterId(reverse.getSrcSwitch().getSwitchId(), reverse.getMeterId());
    }

    /**
     * Allocate free resources for the flow.
     *
     * @param flowPair the flow pair for resource allocation.
     * @return a new flow pair (copied) with allocated resources set.
     */
    public FlowPair allocateFlow(FlowPair flowPair) {
        log.debug("Allocate flow resources for {}.", flowPair);

        int flowCookie = resourceCache.allocateCookie();

        Flow forward = allocateFlowResources(flowPair.getForward());
        forward.setCookie(flowCookie | Flow.FORWARD_FLOW_COOKIE_MASK);

        Flow reverse = allocateFlowResources(flowPair.getReverse());
        reverse.setCookie(flowCookie | Flow.REVERSE_FLOW_COOKIE_MASK);

        return FlowPair.builder().forward(forward).reverse(reverse).build();
    }

    private Flow allocateFlowResources(Flow flow) {
        Flow.FlowBuilder newFlow = flow.toBuilder();
        // Don't allocate a vlan for a single switch flow.
        newFlow.transitVlan(!flow.isOneSwitchFlow() ? resourceCache.allocateVlanId() : 0);

        if (flow.getBandwidth() > 0L) {
            newFlow.meterId(resourceCache.allocateMeterId(flow.getSrcSwitch().getSwitchId()));
        }

        return newFlow.build();
    }

    /**
     * Deallocate the flow resources.
     *
     * @param flowPair flow which resources to deallocate.
     */
    public void deallocateFlow(FlowPair flowPair) {
        log.debug("Deallocate flow resources for {}.", flowPair);

        Flow forward = flowPair.getForward();
        resourceCache.deallocateCookie((int) (ResourceCache.FLOW_COOKIE_VALUE_MASK & forward.getCookie()));
        resourceCache.deallocateVlanId(forward.getTransitVlan());
        if (forward.getMeterId() != null) {
            resourceCache.deallocateMeterId(forward.getSrcSwitch().getSwitchId(), forward.getMeterId());
        }

        Flow reverse = flowPair.getReverse();
        resourceCache.deallocateVlanId(reverse.getTransitVlan());
        if (reverse.getMeterId() != null) {
            resourceCache.deallocateMeterId(reverse.getSrcSwitch().getSwitchId(), reverse.getMeterId());
        }
    }

    public Set<Integer> getAllocatedVlans() {
        return resourceCache.getAllVlanIds();
    }

    public Set<Integer> getAllocatedCookies() {
        return resourceCache.getAllCookies();
    }

    public Map<SwitchId, Set<Integer>> getAllocatedMeters() {
        return resourceCache.getAllMeterIds();
    }
}
