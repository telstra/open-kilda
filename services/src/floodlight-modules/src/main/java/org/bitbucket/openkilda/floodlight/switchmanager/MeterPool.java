package org.bitbucket.openkilda.floodlight.switchmanager;

import org.bitbucket.openkilda.messaging.payload.ResourcePool;

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
    private static final Integer MIN_METER_ID = 1;
    private static final Integer MAX_METER_ID = 4095;
    private final Map<String, ResourcePool> switchMeterPool = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> flowMeterPool = new ConcurrentHashMap<>();

    public synchronized Set<Integer> getMetersByFlow(final String flowId) {
        return flowMeterPool.get(flowId);
    }

    public synchronized Set<Integer> getMetersBySwitch(final String switchId) {
        ResourcePool pool = switchMeterPool.get(switchId);
        return pool == null ? null : pool.dumpPool();
    }

    public synchronized Integer allocate(final String switchId, final String flowId) {
        ResourcePool switchPool = switchMeterPool.get(switchId);
        if (switchPool == null) {
            switchPool = new ResourcePool(MIN_METER_ID, MAX_METER_ID);
            switchMeterPool.put(switchId, switchPool);
        }

        Integer meterId = switchPool.allocate();

        Set<Integer> flowPool = flowMeterPool.get(flowId);
        if (flowPool == null) {
            flowPool = new HashSet<>();
            flowMeterPool.put(flowId, flowPool);
        }

        flowPool.add(meterId);

        return meterId;
    }

    public synchronized Integer deallocate(final String switchId, final String flowId) {
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
            if (switchPool.deallocate(meter)) {
                meterId = meter;
            }
        }

        return meterId;
    }
}
