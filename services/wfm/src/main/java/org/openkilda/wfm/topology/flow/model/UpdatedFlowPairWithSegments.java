/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.model;

import org.openkilda.model.FlowPair;
import org.openkilda.model.PathSegment;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdatedFlowPairWithSegments extends FlowPairWithSegments {
    FlowPair oldFlowPair;
    List<PathSegment> oldForwardSegments;
    List<PathSegment> oldReverseSegments;

    @Builder
    public UpdatedFlowPairWithSegments(FlowPair flowPair,
                                       List<PathSegment> forwardSegments, List<PathSegment> reverseSegments,
                                       FlowPair oldFlowPair,
                                       List<PathSegment> oldForwardSegments, List<PathSegment> oldReverseSegments) {
        super(flowPair, forwardSegments, reverseSegments);
        this.oldFlowPair = oldFlowPair;
        this.oldForwardSegments = oldForwardSegments;
        this.oldReverseSegments = oldReverseSegments;
    }
}
