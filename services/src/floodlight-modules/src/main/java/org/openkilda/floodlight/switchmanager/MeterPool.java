/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager;

import org.openkilda.messaging.payload.ResourcePool;
import org.openkilda.model.SwitchId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allocates and deallocates meter ids.
 */
public class MeterPool {
    private static final Logger logger = LoggerFactory.getLogger(MeterPool.class);
    //@carmine: Probably should also start it at 200 and not 1 …
    // I doubt we’ll end up with 200 Metered flows before we fix this.
    private static final Integer MIN_METER_ID = 200;
    private static final Integer MAX_METER_ID = 4095;
    private final Map<SwitchId, ResourcePool> switchMeterPool = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> flowMeterPool = new ConcurrentHashMap<>();

    public synchronized Set<Integer> getMetersByFlow(final String flowId) {
        return flowMeterPool.get(flowId);
    }

    public synchronized Set<Integer> getMetersBySwitch(final SwitchId switchId) {
        ResourcePool pool = switchMeterPool.get(switchId);
        return pool == null ? null : pool.dumpPool();
    }

    /**
     * The method actually does allocate a method id, not previously allocated.
     *
     * @param switchId switch id.
     * @param flowId flow id.
     * @param meterId meter id.
     * @return allocated meter id.
     */
    public synchronized Integer allocate(final SwitchId switchId, final String flowId, Integer meterId) {
        ResourcePool switchPool = getSwitchPool(switchId);
        Set<Integer> flowPool = getFlowPool(flowId);

        Integer allocatedMeterId = switchPool.allocate(meterId);
        if (allocatedMeterId == null) {
            logger.warn("Meter pool already have record for meter id {}", meterId);
            allocatedMeterId = meterId;
        }
        flowPool.add(allocatedMeterId);

        return allocatedMeterId;
    }

    /**
     * The method actually does allocate a method id, not previously allocated.
     */
    public synchronized Integer allocate(final SwitchId switchId, final String flowId) {
        ResourcePool switchPool = getSwitchPool(switchId);
        Set<Integer> flowPool = getFlowPool(flowId);

        Integer meterId = switchPool.allocate();
        flowPool.add(meterId);

        return meterId;
    }

    /**
     * The method actually does de-allocate a method id, not previously de-allocated.
     */
    public synchronized Integer deallocate(final SwitchId switchId, final String flowId) {
        ResourcePool switchPool = switchMeterPool.get(switchId);
        if (switchPool == null) {
            logger.error("Could not deallocate meter: no such switch {}", switchId);
            return null;
        }

        Set<Integer> flowPool = flowMeterPool.remove(flowId);
        if (flowPool == null) {
            logger.error("Could not deallocate meter: no such flow id={}", flowId);
            return null;
        }

        Integer meterId = null;

        for (Integer meter : flowPool) {
            if (switchPool.deallocate(meter) != null) {
                meterId = meter;
            }
        }

        return meterId;
    }

    private ResourcePool getSwitchPool(final SwitchId switchId) {
        ResourcePool switchPool = switchMeterPool.get(switchId);
        if (switchPool == null) {
            switchPool = new ResourcePool(MIN_METER_ID, MAX_METER_ID);
            switchMeterPool.put(switchId, switchPool);
        }

        return switchPool;
    }

    private Set<Integer> getFlowPool(final String flowId) {
        return flowMeterPool.computeIfAbsent(flowId, k -> new HashSet<>());
    }
}
