/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HandlerResponseMapping<H, R> {
    private final Map<R, H> mapping = new HashMap<>();
    private final Map<H, Set<R>> reverse = new HashMap<>();

    public void add(R reference, H handler) {
        mapping.put(reference, handler);
        reverse.computeIfAbsent(handler, key -> new HashSet<>()).add(reference);
    }

    /**
     * Lookup handler by reference, remove existing mapping after lookup.
     */
    public Optional<H> lookupAndRemove(R reference) {
        H handler = mapping.remove(reference);
        if (handler != null) {
            reverse.getOrDefault(handler, Collections.emptySet()).remove(reference);
            return Optional.of(handler);
        }
        return Optional.empty();
    }

    /**
     * Remove mappings for specified handler.
     */
    public void remove(H handler) {
        for (R entry : reverse.getOrDefault(handler, Collections.emptySet())) {
            mapping.remove(entry);
        }
    }
}
