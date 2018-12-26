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

package org.openkilda.messaging.payload;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class represents resource allocator/deallocator.
 * <p/>
 * TODO: (crimi - 2019.04.17) - why is this class in this package?
 * <p/>
 * (crimi - 2019.04.17) - Changing the underlying mechanism here to leverage "max" as the starting
 * point for where to look next.  If the counter is at max, then start at zero.
 */
public class ResourcePool {
    /**
     * Resource values pool.
     */
    private final Queue<Integer> resources = new ConcurrentLinkedQueue<>();
    private Integer minValue;
    private Integer maxValue;

    /**
     * Instance constructor.
     *
     * @param minValue minimum resource id value
     * @param maxValue maximum resource id value
     */
    public ResourcePool(final Integer minValue, final Integer maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;

        for (int value = minValue; value <= maxValue; value++) {
            resources.add(value);
        }
    }

    /**
     * Allocates resource id.
     *
     * @return allocated resource id
     */
    public Integer allocate() {
        if (!resources.isEmpty()) {
            return resources.poll();
        }
        throw new ArrayIndexOutOfBoundsException("Could not allocate resource: pool is full");
    }

    /**
     * Allocates resource id.
     *
     * @param resourceId resource id
     * @return allocated resource id
     */
    public Integer allocate(Integer resourceId) {
        if (!resources.contains(resourceId)) {
            return null;
        }

        resources.remove(resourceId);
        return resourceId;
    }

    /**
     * Deallocates previously allocated resource id.
     *
     * @param resourceId resource id
     * @return true if specified resource id was previously allocated
     */
    public Integer deallocate(Integer resourceId) {
        if (resources.contains(resourceId)) {
            return null;
        }

        resources.add(resourceId);
        return resourceId;
    }

    /**
     * Returns copy of resource pool.
     *
     * @return {@link ImmutableSet} of allocated resources id
     */
    public Set<Integer> dumpPool() {
        Set<Integer> dumpPool = new HashSet<>();
        for (int value = minValue; value <= maxValue; value++) {
            if (!resources.contains(value)) {
                dumpPool.add(value);
            }
        }
        return ImmutableSet.copyOf(dumpPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("resources", resources)
                .add("min_value", minValue)
                .add("max_value", maxValue)
                .toString();
    }
}
