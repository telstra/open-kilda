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
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.UUID;

@JsonIgnoreProperties({"switch_id"})
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class EgressFlowSegmentBlankRequest
        extends FlowSegmentRequest
        implements IFlowSegmentBlank<EgressFlowSegmentBlankRequest> {
    @JsonProperty("endpoint")
    protected final FlowEndpoint endpoint;

    @JsonProperty("ingress_endpoint")
    protected final FlowEndpoint ingressEndpoint;

    @JsonProperty("islPort")
    protected final Integer islPort;

    @JsonProperty("encapsulation")
    protected final FlowTransitEncapsulation encapsulation;

    EgressFlowSegmentBlankRequest(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            @NonNull FlowEndpoint endpoint, @NonNull FlowEndpoint ingressEndpoint, @NonNull Integer islPort,
            @NonNull FlowTransitEncapsulation encapsulation) {
        super(messageContext, endpoint.getDatapath(), commandId, metadata);

        this.endpoint = endpoint;
        this.ingressEndpoint = ingressEndpoint;
        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    EgressFlowSegmentBlankRequest(@NonNull EgressFlowSegmentBlankRequest other) {
        this(
                other.messageContext, other.commandId, other.metadata, other.endpoint,
                other.ingressEndpoint, other.islPort, other.encapsulation);
    }

    @Override
    public EgressFlowSegmentInstallRequest makeInstallRequest() {
        return new EgressFlowSegmentInstallRequest(this);
    }

    @Override
    public EgressFlowSegmentRemoveRequest makeRemoveRequest() {
        return new EgressFlowSegmentRemoveRequest(this);
    }

    @Override
    public EgressFlowSegmentVerifyRequest makeVerifyRequest() {
        return new EgressFlowSegmentVerifyRequest(this);
    }

    /**
     * Create "blank" resolver - object capable to create any "real" request type.
     */
    @Builder(builderMethodName = "buildResolver")
    public static BlankResolver makeResolver(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, FlowEndpoint ingressEndpoint, Integer islPort,
            FlowTransitEncapsulation encapsulation) {
        EgressFlowSegmentBlankRequest blank = new EgressFlowSegmentBlankRequest(
                messageContext, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation);
        return new BlankResolver(blank);
    }

    public static class BlankResolver extends FlowSegmentBlankResolver<EgressFlowSegmentBlankRequest> {
        BlankResolver(EgressFlowSegmentBlankRequest blank) {
            super(blank);
        }
    }
}
