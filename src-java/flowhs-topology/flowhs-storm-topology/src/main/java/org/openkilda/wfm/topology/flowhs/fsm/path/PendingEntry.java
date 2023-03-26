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
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest.PathChunkType;

import lombok.Value;

import java.util.function.Function;

@Value
class PendingEntry {
    FlowSegmentRequestFactory requestFactory;
    Function<FlowSegmentRequestFactory, FlowSegmentRequest> requestFactoryAdapter;
    PathChunkType type;

    FlowSegmentRequest request;

    int attempt;

    static PendingEntry newAttempt(PendingEntry previous) {
        return new PendingEntry(
                previous.getRequestFactory(), previous.getRequestFactoryAdapter(), previous.getType(),
                previous.getAttempt() + 1);
    }

    public PendingEntry(
            FlowSegmentRequestFactory requestFactory,
            Function<FlowSegmentRequestFactory, FlowSegmentRequest> requestFactoryAdapter, PathChunkType type) {
        this(requestFactory, requestFactoryAdapter, type, 1);
    }

    private PendingEntry(
            FlowSegmentRequestFactory requestFactory,
            Function<FlowSegmentRequestFactory, FlowSegmentRequest> requestFactoryAdapter,
            PathChunkType type, int attempt) {
        this.requestFactory = requestFactory;
        this.requestFactoryAdapter = requestFactoryAdapter;
        this.type = type;
        this.attempt = attempt;

        request = requestFactoryAdapter.apply(requestFactory);
    }
}
