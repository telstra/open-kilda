package org.bitbucket.openkilda.messaging.payload;

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
    private final Set<Integer> resources = ConcurrentHashMap.newKeySet();
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
}
