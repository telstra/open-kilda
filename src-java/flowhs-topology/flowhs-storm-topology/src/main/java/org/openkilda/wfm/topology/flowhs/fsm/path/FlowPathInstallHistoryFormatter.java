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
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;
import org.openkilda.wfm.topology.flowhs.service.common.FlowHistoryCarrier;

import java.util.List;

class FlowPathInstallHistoryFormatter extends FlowPathHistoryFormatter {
    public FlowPathInstallHistoryFormatter(FlowHistoryCarrier carrier) {
        super(carrier);
    }

    @Override
    public void recordChunkProcessing(PathChunkType type) {
        carrier.saveActionToHistory(String.format("Commands for installing %s rules have been sent", type), null);
    }

    @Override
    public void recordSegmentSuccess(
            PathChunkType type, FlowSegmentRequest request, SpeakerFlowSegmentResponse response) {
        carrier.saveActionToHistory(
                "Rule was installed", String.format(
                        "The %s rule was installed: switch %s, cookie %s",
                        type, response.getSwitchId(), request.getCookie()));
    }

    @Override
    public void recordSegmentFailureAndRetry(
            PathChunkType type, FlowSegmentRequest request, FlowErrorResponse errorResponse, int attempt) {
        carrier.saveErrorToHistory(
                "Failed to install rule", String.format(
                        "Failed to install %s rule: commandId %s, switch %s, cookie %s. Error %s. Retrying "
                                + "(attempt %d)",
                        type, request.getCommandId(), errorResponse.getSwitchId(), request.getCookie(), errorResponse,
                        attempt));
    }

    @Override
    public void recordSegmentFailureAndGiveUp(
            PathChunkType type, FlowSegmentRequest request, FlowErrorResponse errorResponse) {
        carrier.saveErrorToHistory(
                "Failed to install rule", String.format(
                        "Failed to install %s rule: commandId %s, switch %s, cookie %s. Error: %s",
                        type, request.getCommandId(), errorResponse.getSwitchId(), request.getCookie(), errorResponse));
    }

    @Override
    public void recordChunkFailure(PathChunkType type, List<FlowSegmentRequest> failRequests) {
        carrier.saveErrorToHistory(String.format(
                "Received error response(s) for %d install commands related to %s chunk", failRequests.size(), type));
    }
}
