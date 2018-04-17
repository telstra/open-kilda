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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class represents resource allocator/deallocator.
 *
 * TODO: (crimi - 2019.04.17) - why is this class in this package?
 *
 * (crimi - 2019.04.17) - Changing the underlying mechanism here to leverage "max" as the starting
 * point for where to look next.  If the counter is at max, then start at zero.
 */
public class ResourcePool {
    /**
     * Resource values pool.
     */
    private final Set<Integer> resources = ConcurrentHashMap.newKeySet();
    private Integer nextId;
    private Integer lower;
    private Integer upper;

    /**
     * Instance constructor.
     *
     * @param minValue minimum resource id value
     * @param maxValue maximum resource id value
     */
    public ResourcePool(final Integer minValue, final Integer maxValue) {
        this.nextId = minValue;
        this.lower = minValue;
        this.upper = maxValue;
    }

    /**
     * Allocates resource id.
     *
     * @return allocated resource id
     */
    public Integer allocate() {
        int range = upper - lower;
        if (resources.size() <= range) {
            // We are just going to loop through everything until we find a free one. Generally
            // speaking this could be inefficient .. but we use "nextId" as a start, and that should
            // have the greatest chance of being available.
            for (int i = 0; i < range; i++) {
                if (nextId > upper)
                    nextId = lower;
                if (resources.add(nextId++)) {
                    return nextId-1;
                }
            }
        }
        throw new ArrayIndexOutOfBoundsException("Could not allocate resource: pool is full");
    }

    /**
     * Allocates resource id.
     *
     * @param id resource id
     * @return allocated resource id
     */
    public Integer allocate(Integer id) {
        // This is added to ensure that if we are adding one or many IDs, we set nextId to the
        // largest of the set. This only affects the next call to allocate() without id, and all
        // it'll do is cause the search to start at this point.
        if (id > nextId)
            nextId = id+1;
        return resources.add(id) ? id : null;
    }

    /**
     * Deallocates previously allocated resource id.
     *
     * @param resourceId resource id
     * @return true if specified resource id was previously allocated
     */
    public Integer deallocate(final Integer resourceId) {
        return resources.remove(resourceId) ? resourceId : null;
    }

    /**
     * Returns copy of resource pool.
     *
     * @return {@link ImmutableSet} of allocated resources id
     */
    public Set<Integer> dumpPool() {
        return ImmutableSet.copyOf(resources);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("resources", resources)
                .add("nextId", nextId)
                .add("lower", lower)
                .add("upper", upper)
                .toString();
    }
}
