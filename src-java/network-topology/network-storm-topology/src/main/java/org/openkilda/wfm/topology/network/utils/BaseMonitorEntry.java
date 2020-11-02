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

import lombok.Getter;
import lombok.NonNull;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

abstract class BaseMonitorEntry<L, S> {
    protected final List<WeakReference<L>> subscribers = new LinkedList<>();

    private final boolean filterOutEqualStatusUpdates;

    @Getter
    @NonNull
    protected S status;

    public BaseMonitorEntry(@NonNull S status) {
        this(true, status);
    }

    public BaseMonitorEntry(boolean filterOutEqualStatusUpdates, @NonNull S status) {
        this.filterOutEqualStatusUpdates = filterOutEqualStatusUpdates;
        this.status = status;
    }

    S subscribe(L listener) {
        subscribers.add(new WeakReference<>(listener));
        return status;
    }

    void update(@NonNull S change) {
        if (filterOutEqualStatusUpdates && Objects.equals(status, change)) {
            return;
        }

        status = change;
        Iterator<WeakReference<L>> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            L entry = iterator.next().get();
            if (entry == null) {
                iterator.remove();
                continue;
            }

            propagate(entry, change);
        }
    }

    boolean isEmpty() {
        return subscribers.isEmpty();
    }

    abstract void propagate(L listener, S change);
}
