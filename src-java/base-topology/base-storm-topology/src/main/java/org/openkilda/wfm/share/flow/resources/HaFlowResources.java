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

import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class HaFlowResources {
    long unmaskedCookie;
    HaPathResources forward;
    HaPathResources reverse;

    @Value
    @Builder
    public static class HaPathResources {
        PathId pathId;
        MeterId sharedMeterId;
        MeterId yPointMeterId;
        GroupId yPointGroupId;
        EncapsulationResources encapsulationResources;
        @Singular
        Map<String, MeterId> subPathMeters;
        @Singular
        Map<String, PathId> subPathIds;

        /**
         * Get resources for specifies sub flow id.
         */
        public PathResources getSubPathResources(String subFlowId) {
            PathId subPathId = subPathIds.get(subFlowId);
            if (subPathId == null) {
                throw new IllegalArgumentException(String.format(
                        "There is no sub path id for sub flow %s. Available path ids: %s",
                        subFlowId, subPathIds.keySet()));
            }
            return new PathResources(subPathId, subPathMeters.get(subFlowId), encapsulationResources);
        }
    }
}
