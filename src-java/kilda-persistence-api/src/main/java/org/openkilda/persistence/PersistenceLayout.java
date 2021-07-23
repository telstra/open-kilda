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

package org.openkilda.persistence;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Helper class used to store/define and query mapping between {@link PersistenceArea} and requested
 * {@link PersistenceImplementation}.
 */
public class PersistenceLayout implements Serializable {
    private final Map<PersistenceArea, PersistenceImplementationType> areaToImplementation = new HashMap<>();
    private final PersistenceImplementationType defaultType;

    public PersistenceLayout(PersistenceImplementationType defaultType) {
        this.defaultType = defaultType;
    }

    /**
     * Add mapping between area and implementation.
     */
    public void add(PersistenceArea area, PersistenceImplementationType implementation) {
        if (implementation != null) {
            areaToImplementation.put(area, implementation);
        }
    }

    public PersistenceImplementationType get(PersistenceArea area) {
        return areaToImplementation.getOrDefault(area, defaultType);
    }

    public PersistenceImplementationType getDefault() {
        return defaultType;
    }

    /**
     * Get set of all referenced implementations.
     */
    public Set<PersistenceImplementationType> getAllImplementations() {
        Set<PersistenceImplementationType> results = new HashSet<>();
        results.add(defaultType);
        results.addAll(areaToImplementation.values());
        return results;
    }
}
