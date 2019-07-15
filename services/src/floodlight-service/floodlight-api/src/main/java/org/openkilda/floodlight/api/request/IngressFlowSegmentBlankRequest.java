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
import org.openkilda.model.MeterConfig;

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
public class IngressFlowSegmentBlankRequest extends IngressFlowSegmentRequest
        implements IFlowSegmentBlank<IngressFlowSegmentBlankRequest> {
    @JsonProperty("islPort")
    protected final Integer islPort;

    @JsonProperty("encapsulation")
    protected final FlowTransitEncapsulation encapsulation;

    IngressFlowSegmentBlankRequest(
            MessageContext context, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, @NonNull Integer islPort,
            @NonNull FlowTransitEncapsulation encapsulation) {
        super(context, commandId, metadata, endpoint, meterConfig);

        this.islPort = islPort;
        this.encapsulation = encapsulation;
    }

    IngressFlowSegmentBlankRequest(@NonNull IngressFlowSegmentBlankRequest other) {
        this(
                other.messageContext, other.commandId, other.metadata, other.endpoint, other.meterConfig,
                other.islPort, other.encapsulation);
    }

    @Override
    public IngressFlowSegmentInstallRequest makeInstallRequest() {
        return new IngressFlowSegmentInstallRequest(this);
    }

    @Override
    public IngressFlowSegmentRemoveRequest makeRemoveRequest() {
        return new IngressFlowSegmentRemoveRequest(this);
    }

    @Override
    public IngressFlowSegmentVerifyRequest makeVerifyRequest() {
        return new IngressFlowSegmentVerifyRequest(this);
    }

    /**
     * Create "blank" resolver - object capable to create any "real" request type.
     */
    @Builder(builderMethodName = "buildResolver")
    public static BlankResolver makeResolver(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, Integer islPort, FlowTransitEncapsulation encapsulation) {
        IngressFlowSegmentBlankRequest blank = new IngressFlowSegmentBlankRequest(
                messageContext, commandId, metadata, endpoint, meterConfig, islPort, encapsulation);
        return new BlankResolver(blank);
    }

    public static class BlankResolver
            extends FlowSegmentBlankResolver<IngressFlowSegmentBlankRequest> {
        BlankResolver(IFlowSegmentBlank<IngressFlowSegmentBlankRequest> blank) {
            super(blank);
        }
    }
}
