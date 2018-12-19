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

package org.openkilda.wfm.share.cache;

import org.openkilda.messaging.payload.ResourcePool;
import org.openkilda.model.SwitchId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ResourceManager class contains basic operations on resources.
 */
public class ResourceCache {
    /**
     * Flow cookie value mask.
     */
    public static final long FLOW_COOKIE_VALUE_MASK = 0x00000000FFFFFFFFL;

    /**
     * Minimum vlan id value.
     */
    @VisibleForTesting
    public static final int MIN_VLAN_ID = 2;

    /**
     * Maximum vlan id value.
     */
    @VisibleForTesting
    public static final int MAX_VLAN_ID = 4094;

    /**
     * Minimum meter id value.
     * This value is used to allocate meter IDs for flows. But also we need to allocate meter IDs for default rules.
     * To do it special mask 'PACKET_IN_RULES_METERS_MASK' is used. With the help of this mask we take last 5 bits of
     * default rule cookie to create meter ID. That means we have range 1..31 for default rule meter IDs.
     * MIN_METER_ID is used to do not intersect flow meter IDs with this range.
     */
    @VisibleForTesting
    public static final int MIN_METER_ID = 32;

    /**
     * Maximum meter id value.
     * NB: Should be the same as VLAN range at the least, could be more. The formula ensures we have a sufficient range.
     * As centecs have limit of max value equals to 2560 we set it to 2500.
     */
    @VisibleForTesting
    public static final int MAX_METER_ID = 2500;

    /**
     * Maximum cookie value.
     */
    static final int MAX_COOKIE = 128 * 1024;

    /**
     * Minimum cookie value.
     */
    static final int MIN_COOKIE = 1;

    /**
     * Meter pool by switch.
     */
    private final Map<SwitchId, ResourcePool> meterPool = new ConcurrentHashMap<>();

    /**
     * Cookie pool.
     */
    private final ResourcePool cookiePool = new ResourcePool(MIN_COOKIE, MAX_COOKIE);

    /**
     * Transit vlan id pool.
     */
    private final ResourcePool vlanPool = new ResourcePool(MIN_VLAN_ID, MAX_VLAN_ID);

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
    public synchronized Integer allocateMeterId(SwitchId switchId) {
        return meterPool.computeIfAbsent(switchId, k -> new ResourcePool(MIN_METER_ID, MAX_METER_ID)).allocate();
    }

    /**
     * Allocates meter id.
     *
     * @param switchId switch id
     * @param meterId  meter id value
     * @return allocated meter id value
     */
    public synchronized Integer allocateMeterId(SwitchId switchId, Integer meterId) {
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
     * @param meterId  meter id value
     * @return deallocated meter id value or null if value was not allocated earlier
     */
    public synchronized Integer deallocateMeterId(SwitchId switchId, Integer meterId) {
        ResourcePool switchMeterPool = meterPool.get(switchId);
        return switchMeterPool != null ? switchMeterPool.deallocate(meterId) : null;
    }

    /**
     * Deallocates meter id for switch.
     *
     * @param switchId switch id
     * @return deallocated meter id values
     */
    public synchronized Set<Integer> deallocateMeterId(SwitchId switchId) {
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
    public Set<Integer> getAllMeterIds(SwitchId switchId) {
        return meterPool.containsKey(switchId) ? meterPool.get(switchId).dumpPool() : Collections.emptySet();
    }

    /**
     * Gets all allocated meter id values.
     *
     * @return all allocated meter id values
     */
    public Map<SwitchId, Set<Integer>> getAllMeterIds() {
        return meterPool.entrySet().stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        e -> e.getValue().dumpPool())
                );
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
