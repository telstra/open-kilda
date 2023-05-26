/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;

import org.openkilda.model.PathId;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Value
@Builder
public class HaPathIdsPair {
    HaFlowPathIds forward;
    HaFlowPathIds reverse;

    /**
     * Gets all ha-flow path IDs.
     */
    public List<PathId> getAllHaFlowPathIds() {
        List<PathId> result = new ArrayList<>();
        if (forward != null && forward.getHaPathId() != null) {
            result.add(forward.getHaPathId());
        }
        if (reverse != null && reverse.getHaPathId() != null) {
            result.add(reverse.getHaPathId());
        }
        return result;
    }

    /**
     * Gets all ha-flow sub path IDs.
     */
    public List<PathId> getAllSubPathIds() {
        List<PathId> result = new ArrayList<>();
        if (forward != null && forward.subPathIds != null) {
            forward.getSubPathIds().values().stream().filter(Objects::nonNull).forEach(result::add);
        }
        if (reverse != null && reverse.subPathIds != null) {
            reverse.getSubPathIds().values().stream().filter(Objects::nonNull).forEach(result::add);
        }
        return result;
    }

    @Value
    @Builder
    public static class HaFlowPathIds {
        PathId haPathId;
        @Singular
        Map<String, PathId> subPathIds;

        /**
         * Gets ha-sub path id by ha-sub flow id.
         */
        public PathId getSubPathId(String haSubFlowId) {
            if (subPathIds.containsKey(haSubFlowId)) {
                return subPathIds.get(haSubFlowId);
            }
            throw new IllegalArgumentException(format("Couldn't find sub path id by haSubFlowId %s. Valid keys are: %s",
                    haSubFlowId, subPathIds.keySet()));
        }
    }
}


