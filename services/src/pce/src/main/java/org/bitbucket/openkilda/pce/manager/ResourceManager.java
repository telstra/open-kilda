package org.bitbucket.openkilda.pce.manager;

import org.bitbucket.openkilda.messaging.payload.ResourcePool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResourceManager class contains basic operations on resources.
 */
class ResourceManager {
    static final int MAX_METER_ID = 4095;
    static final int MIN_METER_ID = 1;
    static final int MAX_VLAN_ID = 4094;
    static final int MIN_VLAN_ID = 2;
    static final int MAX_COOKIE = 4095;
    static final int MIN_COOKIE = 1;

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);

    /**
     * Meter pool by switch.
     */
    private final Map<String, ResourcePool> meterPool = new ConcurrentHashMap<>();

    /**
     * Cookie pool.
     */
    private final ResourcePool cookiePool = new ResourcePool(MIN_COOKIE, MAX_COOKIE);

    /**
     * Transit vlan id pool.
     */
    private final ResourcePool vlanPool = new ResourcePool(MIN_VLAN_ID, MAX_VLAN_ID);

    /**
     * Instance constructor.
     */
    ResourceManager() {
    }

    public void clear() {
        cookiePool.dumpPool().forEach(cookiePool::deallocate);
        vlanPool.dumpPool().forEach(vlanPool::deallocate);
        meterPool.clear();
    }

    Integer allocateCookie() {
        return cookiePool.allocate();
    }

    Integer allocateCookie(Integer cookie) {
        return cookiePool.allocate(cookie);
    }

    Integer deallocateCookie(Integer cookie) {
        return cookiePool.deallocate(cookie);
    }

    Integer allocateVlanId() {
        return vlanPool.allocate();
    }

    Integer allocateVlanId(Integer vlanId) {
        return vlanPool.allocate(vlanId);
    }

    Integer deallocateVlanId(Integer vlanId) {
        return vlanPool.deallocate(vlanId);
    }

    synchronized Integer allocateMeterId(String switchId) {
        return meterPool.computeIfAbsent(switchId, k -> new ResourcePool(MIN_METER_ID, MAX_METER_ID)).allocate();
    }

    synchronized Integer allocateMeterId(String switchId, Integer meterId) {
        return meterPool.computeIfAbsent(switchId, k -> new ResourcePool(MIN_METER_ID, MAX_METER_ID)).allocate(meterId);
    }

    synchronized Integer deallocateMeterId(String switchId, Integer meterId) {
        return meterPool.get(switchId).deallocate(meterId);
    }

    synchronized Set<Integer> deallocateMeterId(String switchId) {
        return meterPool.remove(switchId).dumpPool();
    }

    Set<Integer> getAllCookies() {
        return cookiePool.dumpPool();
    }

    Set<Integer> getAllVlanIds() {
        return vlanPool.dumpPool();
    }

    Set<Integer> getAllMeterIds(String switchId) {
        return meterPool.containsKey(switchId) ? meterPool.get(switchId).dumpPool() : Collections.emptySet();
    }
}
