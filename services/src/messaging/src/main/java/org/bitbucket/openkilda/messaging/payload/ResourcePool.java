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

package org.bitbucket.openkilda.messaging.payload;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class represents resource allocator/deallocator.
 */
public class ResourcePool {
    /**
     * Resource values pool.
     */
    private final Set<Integer> resources = ConcurrentHashMap.newKeySet();

    /**
     * Resource range of values.
     */
    private final Range<Integer> range;

    /**
     * Instance constructor.
     *
     * @param minValue minimum resource id value
     * @param maxValue maximum resource id value
     */
    public ResourcePool(final Integer minValue, final Integer maxValue) {
        this.range = Range.closed(minValue, maxValue);
    }

    /**
     * Allocates resource id.
     *
     * @return allocated resource id
     */
    public Integer allocate() {
        for (Integer id : ContiguousSet.create(range, DiscreteDomain.integers())) {
            if (resources.add(id)) {
                return id;
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
                .add("range", range)
                .add("resources", resources)
                .toString();
    }
}
