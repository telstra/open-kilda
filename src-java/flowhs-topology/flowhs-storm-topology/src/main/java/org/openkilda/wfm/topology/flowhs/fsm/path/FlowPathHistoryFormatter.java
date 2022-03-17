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

package org.openkilda.wfm.topology.flowhs.fsm.path;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;
import org.openkilda.wfm.topology.flowhs.service.common.FlowHistoryCarrier;

import java.util.List;

abstract class FlowPathHistoryFormatter {
    protected final FlowHistoryCarrier carrier;

    public FlowPathHistoryFormatter(FlowHistoryCarrier carrier) {
        this.carrier = carrier;
    }

    public abstract void recordChunkProcessing(FlowPathRequest.PathChunkType type);

    public abstract void recordSegmentSuccess(
            PathChunkType type, FlowSegmentRequest request, SpeakerFlowSegmentResponse response);

    public abstract void recordSegmentFailureAndRetry(
            PathChunkType type, FlowSegmentRequest request, FlowErrorResponse errorResponse, int attempt);

    public abstract void recordSegmentFailureAndGiveUp(
            PathChunkType type, FlowSegmentRequest request, FlowErrorResponse errorResponse);

    public abstract void recordChunkFailure(PathChunkType type, List<FlowSegmentRequest> failRequests);
}
