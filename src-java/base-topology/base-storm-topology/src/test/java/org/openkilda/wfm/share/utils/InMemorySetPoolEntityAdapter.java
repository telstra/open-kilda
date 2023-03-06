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

package org.openkilda.wfm.share.utils;

import java.util.HashSet;
import java.util.Optional;

public class InMemorySetPoolEntityAdapter implements PoolEntityAdapter {
    private final HashSet<Long> allocated = new HashSet<>();

    public void release(long entity) {
        allocated.remove(entity);
    }

    @Override
    public boolean allocateSpecificId(long entityId) {
        return allocated.add(entityId);
    }

    @Override
    public Optional<Long> allocateFirstInRange(long idMinimum, long idMaximum) {
        for (long idx = idMinimum; idx <= idMaximum; idx++) {
            if (allocated.add(idx)) {
                return Optional.of(idx);
            }
        }
        return Optional.empty();
    }

    @Override
    public String formatResourceNotAvailableMessage() {
        return "no pool entity available (in memory)";
    }
}
