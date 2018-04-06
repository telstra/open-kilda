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

package org.openkilda.pce.cache;

import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.ResourcePool;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ResourceManager class contains basic operations on resources.
 */
public class ResourceCache extends Cache {
    /**
     * Flow cookie value mask.
     */
    public static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

    /**
     * Forward flow cookie mask.
     */
    public static final long FORWARD_FLOW_COOKIE_MASK = 0x4000000000000000L;

    /**
     * Reverse flow cookie mask.
     */
    public static final long REVERSE_FLOW_COOKIE_MASK = 0x2000000000000000L;

    /**
     * Maximum meter id value.
     */
    static final int MAX_METER_ID = 4095;

    /**
     * Minimum meter id value.
     */
    static final int MIN_METER_ID = 1;

    /**
     * Maximum vlan id value.
     */
    static final int MAX_VLAN_ID = 4094;

    /**
     * Minimum vlan id value.
     */
    static final int MIN_VLAN_ID = 2;

    /**
     * Maximum cookie value.
     */
    static final int MAX_COOKIE = 128*1024;

    /**
     * Minimum cookie value.
     */
    static final int MIN_COOKIE = 1;

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ResourceCache.class);

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
    public ResourceCache() {
    }

    /**
     * Clears allocated resources.
     */
    public void clear() {
        cookiePool.dumpPool().forEach(cookiePool::deallocate);
        vlanPool.dumpPool().forEach(vlanPool::deallocate);
        meterPool.clear();
    }

    /**
     * Allocates cookie.
     *
     * @return allocated cookie value
     */
    public Integer allocateCookie() {
        return cookiePool.allocate();
    }

    /**
     * Allocates cookie.
     *
     * @param cookie cookie value
     * @return allocated cookie value
     */
    public Integer allocateCookie(Integer cookie) {
        if (cookie == 0) {
            return cookiePool.allocate();
        } else {
            cookiePool.allocate(cookie);
            return cookie;
        }
    }

    /**
     * Deallocates cookie.
     *
     * @param cookie cookie value
     * @return deallocated cookie value or null if value was not allocated earlier
     */
    public Integer deallocateCookie(Integer cookie) {
        return cookiePool.deallocate(cookie);
    }

    /**
     * Allocates vlan id.
     *
     * @return allocated vlan id value
     */
    public Integer allocateVlanId() {
        return vlanPool.allocate();
    }

    /**
     * Allocates vlan id.
     *
     * @param vlanId vlan id value
     * @return allocated vlan id value
     */
    public Integer allocateVlanId(Integer vlanId) {
        if (vlanId == 0) {
            return vlanPool.allocate();
        } else {
            vlanPool.allocate(vlanId);
            return vlanId;
        }
    }

    /**
     * Deallocates vlan id.
     *
     * @param vlanId vlan id value
     * @return deallocated vlan id value or null if value was not allocated earlier
     */
    public Integer deallocateVlanId(Integer vlanId) {
        return vlanPool.deallocate(vlanId);
    }

    /**
     * Allocates meter id.
     *
     * @param switchId switch id
     * @return allocated meter id value
     */
    public synchronized Integer allocateMeterId(String switchId) {
        return meterPool.computeIfAbsent(switchId, k -> new ResourcePool(MIN_METER_ID, MAX_METER_ID)).allocate();
    }

    /**
     * Allocates meter id.
     *
     * @param switchId switch id
     * @param meterId  meter id value
     * @return allocated meter id value
     */
    public synchronized Integer allocateMeterId(String switchId, Integer meterId) {
        if (meterId == 0) {
            return meterPool.computeIfAbsent(switchId, k -> new ResourcePool(MIN_METER_ID, MAX_METER_ID))
                    .allocate();
        } else {
            meterPool.computeIfAbsent(switchId, k -> new ResourcePool(MIN_METER_ID, MAX_METER_ID))
                    .allocate(meterId);
            return meterId;
        }
    }

    /**
     * Deallocates meter id.
     *
     * @param switchId switch id
     * @param meterId meter id value
     * @return deallocated meter id value or null if value was not allocated earlier
     */
    public synchronized Integer deallocateMeterId(String switchId, Integer meterId) {
        ResourcePool switchMeterPool = meterPool.get(switchId);
        return switchMeterPool != null ? switchMeterPool.deallocate(meterId) : null;
    }

    /**
     * Deallocates meter id for switch.
     *
     * @param switchId switch id
     * @return deallocated meter id values
     */
    public synchronized Set<Integer> deallocateMeterId(String switchId) {
        ResourcePool switchMeterPool = meterPool.remove(switchId);
        return switchMeterPool != null ? switchMeterPool.dumpPool() : null;
    }

    /**
     * Gets all allocated cookie values.
     *
     * @return all allocated cookie values
     */
    public Set<Integer> getAllCookies() {
        return cookiePool.dumpPool();
    }

    /**
     * Gets all vlan id values.
     *
     * @return all allocated vlan id values
     */
    public Set<Integer> getAllVlanIds() {
        return vlanPool.dumpPool();
    }

    /**
     * Gets all allocated meter id values.
     *
     * @param switchId switch id
     * @return all allocated meter id values
     */
    public Set<Integer> getAllMeterIds(String switchId) {
        return meterPool.containsKey(switchId) ? meterPool.get(switchId).dumpPool() : Collections.emptySet();
    }

    /**
     * Allocates flow resources. All flows come here .. single switch and multi switch flows.
     *
     * @param flow flow
     */
    public void allocateFlow(ImmutablePair<Flow, Flow> flow) {

        if (flow.left != null) {
            allocateCookie((int) (FLOW_COOKIE_VALUE_MASK & flow.left.getCookie()));
            if (!flow.left.isOneSwitchFlow()) {
                // Don't allocate if one switch .. it is zero
                // .. and allocateVlanId *will* allocate if it is zero, which consumes a very
                // .. limited resource unnecessarily
                allocateVlanId(flow.left.getTransitVlan());
            }
            allocateMeterId(flow.left.getSourceSwitch(), flow.left.getMeterId());
        }

        if (flow.right != null) {
            if (!flow.right.isOneSwitchFlow()) {
                // Don't allocate if one switch .. it is zero
                // .. and allocateVlanId *will* allocate if it is zero, which consumes a very
                // .. limited resource unnecessarily
                allocateVlanId(flow.right.getTransitVlan());
            }
            allocateMeterId(flow.right.getSourceSwitch(), flow.right.getMeterId());
        }
    }

    /**
     * Deallocates flow resources.
     *
     * @param flow flow
     */
    public void deallocateFlow(ImmutablePair<Flow, Flow> flow) {
        deallocateCookie((int) (FLOW_COOKIE_VALUE_MASK & flow.left.getCookie()));

        deallocateVlanId(flow.left.getTransitVlan());
        deallocateMeterId(flow.left.getSourceSwitch(), flow.left.getMeterId());

        if (flow.right != null) {
            deallocateVlanId(flow.right.getTransitVlan());
            deallocateMeterId(flow.right.getSourceSwitch(), flow.right.getMeterId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("meters", meterPool)
                .add("cookies", cookiePool)
                .add("vlans", vlanPool)
                .toString();
    }
}
