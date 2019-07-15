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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm.Event;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeakerCommandObserver {

    private final SpeakerCommandFsm commandExecutor;

    public SpeakerCommandObserver(SpeakerCommandFsm.Builder builder, FlowSegmentRequest request) {
        commandExecutor = builder.newInstance(request);

        commandExecutor.start();
        commandExecutor.fire(Event.ACTIVATE);
    }

    /**
     * Processes a response. If command wasn't executed successfully then it will be retried if limit is not exceeded.
     * @param response a response from a speaker.
     */
    public boolean handleResponse(SpeakerFlowSegmentResponse response) {
        commandExecutor.fire(Event.REPLY, response);
        return commandExecutor.isTerminated();
    }
}
