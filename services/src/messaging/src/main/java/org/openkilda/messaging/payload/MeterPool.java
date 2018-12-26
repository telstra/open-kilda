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

package org.openkilda.messaging.payload;

import static com.google.common.collect.Sets.union;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class represents meter resource allocator/deallocator.
 */
public class MeterPool {
    /**
     * Meter values pool.
     */
    private final Queue<Integer> meterIds = new ConcurrentLinkedQueue<>();
    private final Set<Integer> oldMeterIds = ConcurrentHashMap.newKeySet();
    private Integer minValue;
    private Integer maxValue;

    /**
     * Instance constructor.
     *
     * @param minValue minimum meter id value
     * @param maxValue maximum meter id value
     */
    public MeterPool(final Integer minValue, final Integer maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;

        for (int value = minValue; value <= maxValue; value++) {
            meterIds.add(value);
        }
    }

    /**
     * Allocates meter id.
     *
     * @return allocated meter id
     */
    public Integer allocate() {
        if (!meterIds.isEmpty()) {
            return meterIds.poll();
        }
        throw new ArrayIndexOutOfBoundsException("Could not allocate resource: pool is full");
    }

    /**
     * Allocates meter id.
     *
     * @param meterId meter id
     * @return allocated meter id
     */
    public Integer allocate(Integer meterId) {
        if (meterId < minValue || meterId > maxValue) {
            return oldMeterIds.add(meterId) ? meterId : null;
        }

        if (!meterIds.contains(meterId)) {
            return null;
        }

        meterIds.remove(meterId);
        return meterId;
    }

    /**
     * Deallocates previously allocated meter id.
     *
     * @param meterId meter id
     * @return true if specified meter id was previously allocated
     */
    public Integer deallocate(Integer meterId) {
        if (meterId < minValue || meterId > maxValue) {
            return oldMeterIds.remove(meterId) ? meterId : null;
        }

        if (meterIds.contains(meterId)) {
            return null;
        }

        meterIds.add(meterId);
        return meterId;
    }

    /**
     * Returns copy of meter pool.
     *
     * @return {@link ImmutableSet} of allocated meter id
     */
    public Set<Integer> dumpPool() {
        Set<Integer> meterIdsSet = new HashSet<>(meterIds);
        Set<Integer> dumpPool = new HashSet<>();
        for (int value = minValue; value <= maxValue; value++) {
            if (!meterIdsSet.contains(value)) {
                dumpPool.add(value);
            }
        }
        return ImmutableSet.copyOf(union(dumpPool, oldMeterIds));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("resources", dumpPool())
                .add("min_value", minValue)
                .add("max_value", maxValue)
                .toString();
    }
}
