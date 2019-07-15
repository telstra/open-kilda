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

package org.openkilda.floodlight.api.request;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class FlowSegmentRequest extends SpeakerRequest {
    @JsonProperty("metadata")
    protected final FlowSegmentMetadata metadata;

    public FlowSegmentRequest(
            MessageContext context, SwitchId switchId, UUID commandId, @NonNull FlowSegmentMetadata metadata) {
        super(context, switchId, commandId);
        this.metadata = metadata;
    }

    @JsonIgnore
    public Cookie getCookie() {
        return metadata.getCookie();
    }

    @JsonIgnore
    public String getFlowId() {
        return metadata.getFlowId();
    }

    /**
     * Detect is the provided request is an install request.
     */
    public static boolean isInstallRequest(FlowSegmentRequest request) {
        return request instanceof TransitFlowSegmentInstallRequest
                || request instanceof IngressFlowSegmentInstallRequest
                || request instanceof OneSwitchFlowInstallRequest
                || request instanceof EgressFlowSegmentInstallRequest;
    }

    /**
     * Detect is the provided request is a remove request.
     */
    public static boolean isRemoveRequest(FlowSegmentRequest request) {
        return request instanceof TransitFlowSegmentRemoveRequest
                || request instanceof IngressFlowSegmentRemoveRequest
                || request instanceof OneSwitchFlowRemoveRequest
                || request instanceof EgressFlowSegmentRemoveRequest;
    }

    /**
     * Detect is the provided request is a verify request.
     */
    public static boolean isVerifyRequest(FlowSegmentRequest request) {
        return request instanceof TransitFlowSegmentVerifyRequest
                || request instanceof IngressFlowSegmentVerifyRequest
                || request instanceof OneSwitchFlowVerifyRequest
                || request instanceof EgressFlowSegmentVerifyRequest;
    }
}
