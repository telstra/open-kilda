/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.network.utils;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseMonitor<R, L, S, E extends BaseMonitorEntry<L, S>> {
    protected final Map<R, E> monitors = new HashMap<>();

    public S subscribe(R switchId, L listener) {
        return monitors.computeIfAbsent(switchId, this::makeEntry)
                .subscribe(listener);
    }

    /**
     * Update monitor status - propagate status to all subscribers.
     */
    public void update(R reference, S change) {
        E entry = monitors.computeIfAbsent(reference, this::makeEntry);
        entry.update(change);
        if (entry.isEmpty()) {
            monitors.remove(reference);
        }
    }

    protected abstract E makeEntry(R reference);
}
