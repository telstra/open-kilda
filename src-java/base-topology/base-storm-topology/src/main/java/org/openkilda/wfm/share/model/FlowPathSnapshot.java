/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.model;

import org.openkilda.model.FlowPath;
import org.openkilda.model.SharedOfFlow;
import org.openkilda.wfm.share.flow.resources.FlowResources;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class FlowPathSnapshot {
    private final FlowPath path;

    private final FlowResources.PathResources resources;

    private final SharedOfFlowStatus sharedIngressSegmentOuterVlanMatchStatus;

    private final boolean removeCustomerPortSharedCatchRule;

    public static FlowPathSnapshotBuilder builder(FlowPath path) {
        return new FlowPathSnapshotBuilder()
                .path(path);
    }

    public static class FlowPathSnapshotBuilder {
        /**
         * Map {@code SharedOfFlow.SharedOfFlowType} to one of the fields.
         */
        public FlowPathSnapshotBuilder sharedOfReference(
                SharedOfFlow.SharedOfFlowType type, SharedOfFlowStatus status) {
            if (type == SharedOfFlow.SharedOfFlowType.INGRESS_OUTER_VLAN_MATCH) {
                sharedIngressSegmentOuterVlanMatchStatus(status);
            } else {
                throw new IllegalArgumentException(String.format("Unknown shared OF Flow type %s", type));
            }

            return this;
        }
    }
}
