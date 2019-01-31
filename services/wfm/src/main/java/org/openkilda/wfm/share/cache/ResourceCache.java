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

import static org.openkilda.model.MeterId.MAX_FLOW_METER_ID;
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID;

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
    private final Map<SwitchId, MeterPool> meterPool = new ConcurrentHashMap<>();

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
        return meterPool.computeIfAbsent(switchId, k -> new MeterPool(MIN_FLOW_METER_ID, MAX_FLOW_METER_ID)).allocate();
    }

    /**
     * Allocates meter id.
     *
     * @param switchId switch id
     * @param meterId  meter id value
     * @return allocated meter id value
     */
    public synchronized Integer allocateMeterId(SwitchId switchId, Integer meterId) {
        if (meterId != null && meterId != 0) {
            meterPool.computeIfAbsent(switchId, k -> new MeterPool(MIN_FLOW_METER_ID, MAX_FLOW_METER_ID))
                    .allocate(meterId);
            return meterId;
        }
        return null;
    }

    /**
     * Deallocates meter id.
     *
     * @param switchId switch id
     * @param meterId  meter id value
     * @return deallocated meter id value or null if value was not allocated earlier
     */
    public synchronized Integer deallocateMeterId(SwitchId switchId, Integer meterId) {
        MeterPool switchMeterPool = meterPool.get(switchId);
        return switchMeterPool != null ? switchMeterPool.deallocate(meterId) : null;
    }

    /**
     * Deallocates meter id for switch.
     *
     * @param switchId switch id
     * @return deallocated meter id values
     */
    public synchronized Set<Integer> deallocateMeterId(SwitchId switchId) {
        MeterPool switchMeterPool = meterPool.remove(switchId);
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
