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

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import lombok.Builder;

import java.util.Optional;
import java.util.UUID;

public final class FlowSegmentRequestFactoryFilter implements FlowSegmentRequestFactory {

    private final FlowSegmentRequestFactory targetFactory;

    private final boolean allowCreate;

    private final boolean allowRemove;

    private final boolean allowVerify;

    @Builder(toBuilder = true)
    private FlowSegmentRequestFactoryFilter(
            FlowSegmentRequestFactory targetFactory, boolean allowCreate, boolean allowRemove, boolean allowVerify) {
        this.targetFactory = targetFactory;
        this.allowCreate = allowCreate;
        this.allowRemove = allowRemove;
        this.allowVerify = allowVerify;
    }

    @Override
    public Optional<? extends FlowSegmentRequest> makeInstallRequest(UUID commandId) {
        if (! allowCreate) {
            return Optional.empty();
        }
        return targetFactory.makeInstallRequest(commandId);
    }

    @Override
    public Optional<? extends FlowSegmentRequest> makeRemoveRequest(UUID commandId) {
        if (! allowRemove) {
            return Optional.empty();
        }
        return targetFactory.makeRemoveRequest(commandId);
    }

    @Override
    public Optional<? extends FlowSegmentRequest> makeVerifyRequest(UUID commandId) {
        if (! allowVerify) {
            return Optional.empty();
        }
        return targetFactory.makeVerifyRequest(commandId);
    }

    @Override
    public UUID getCommandId() {
        return targetFactory.getCommandId();
    }

    @Override
    public SwitchId getSwitchId() {
        return targetFactory.getSwitchId();
    }

    @Override
    public String getFlowId() {
        return targetFactory.getFlowId();
    }

    @Override
    public Cookie getCookie() {
        return targetFactory.getCookie();
    }

    public static FlowSegmentRequestFactoryFilterBuilder builder(FlowSegmentRequestFactory requestFactory) {
        return new FlowSegmentRequestFactoryFilterBuilder()
                .targetFactory(requestFactory);
    }
}
