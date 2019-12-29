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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.fsm.common.WithHistorySupportFsm;

import java.util.UUID;

public abstract class SpeakerRequestRepeatAction<T extends WithHistorySupportFsm<T, S, E, C>, S, E, C>
        extends HistoryRecordingAction<T, S, E, C> {
    protected FlowSegmentRequest makeInstallRequest(FlowSegmentRequestFactory requestFactory, UUID commandId) {
        return requestFactory.makeInstallRequest(commandId)
                .orElseThrow(() -> produceRequestRecreateMissingError(commandId, "install"));
    }

    protected FlowSegmentRequest makeRemoveRequest(FlowSegmentRequestFactory requestFactory, UUID commandId) {
        return requestFactory.makeRemoveRequest(commandId)
                .orElseThrow(() -> produceRequestRecreateMissingError(commandId, "remove"));
    }

    protected FlowSegmentRequest makeVerifyRequest(FlowSegmentRequestFactory requestFactory, UUID commandId) {
        return requestFactory.makeVerifyRequest(commandId)
                .orElseThrow(() -> produceRequestRecreateMissingError(commandId, "verify"));
    }

    private IllegalStateException produceRequestRecreateMissingError(UUID commandId, String type) {
        return new IllegalStateException(String.format("Unable to make %s request for command %s", type, commandId));
    }
}
