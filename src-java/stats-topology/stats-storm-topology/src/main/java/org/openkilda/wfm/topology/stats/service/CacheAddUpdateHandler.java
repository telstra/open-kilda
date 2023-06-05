/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.stats.service;

import org.openkilda.wfm.topology.stats.model.CookieCacheKey;
import org.openkilda.wfm.topology.stats.model.GroupCacheKey;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeasurePoint;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

public class CacheAddUpdateHandler extends BaseCacheChangeHandler {
    public CacheAddUpdateHandler(
            HashSetValuedHashMap<CookieCacheKey, KildaEntryDescriptor> cookieToEntry,
            HashSetValuedHashMap<MeterCacheKey, KildaEntryDescriptor> meterToEntry,
            HashSetValuedHashMap<GroupCacheKey, KildaEntryDescriptor> groupToEntry) {
        super(cookieToEntry, meterToEntry, groupToEntry);
    }

    /**
     * Caches the given entry with the specified key in the cookieToEntry map.
     * It is vital to check if the value with the same measurePoint already exist in the cookieToEntry cache,
     * and remove it if so, this condition is important during any path update.
     *
     * @param key   the CookieCacheKey used as the key in the map
     * @param entry the KildaEntryDescriptor to be cached
     */
    @Override
    protected void cacheAction(CookieCacheKey key, KildaEntryDescriptor entry) {
        if (cookieToEntry.containsKey(key)) {
            cookieToEntry.get(key).stream()
                    .filter(descriptor -> entry.getMeasurePoint() == descriptor.getMeasurePoint())
                    .findFirst().ifPresent(descriptor -> cookieToEntry.removeMapping(key, descriptor));
        }
        cookieToEntry.put(key, entry);
    }

    @Override
    protected void cacheAction(MeterCacheKey key, KildaEntryDescriptor entry) {
        if (!meterToEntry.get(key).isEmpty() && MeasurePoint.HA_FLOW_Y_POINT == entry.getMeasurePoint()) {
            if (meterToEntry.get(key).stream().noneMatch(descriptor ->
                    MeasurePoint.HA_FLOW_Y_POINT == descriptor.getMeasurePoint())) {
                meterToEntry.put(key, entry);
            }
        } else {
            meterToEntry.put(key, entry);
        }
    }

    @Override
    protected void cacheAction(GroupCacheKey key, KildaEntryDescriptor entry) {
        groupToEntry.put(key, entry);
    }
}
