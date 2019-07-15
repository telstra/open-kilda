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
public class OneSwitchFlowBlankRequest extends IngressFlowSegmentRequest
        implements IFlowSegmentBlank<OneSwitchFlowBlankRequest> {
    @JsonProperty("egress_endpoint")
    protected final FlowEndpoint egressEndpoint;

    OneSwitchFlowBlankRequest(
            MessageContext context, UUID commandId, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
            MeterConfig meterConfig, @NonNull FlowEndpoint egressEndpoint) {
        super(context, commandId, metadata, endpoint, meterConfig);

        if (! getSwitchId().equals(egressEndpoint.getDatapath())) {
            throw new IllegalArgumentException(String.format(
                    "Ingress(%s) and egress(%s) switches must match in %s",
                    getSwitchId(), egressEndpoint.getDatapath(), getClass().getName()));
        }

        this.egressEndpoint = egressEndpoint;
    }

    OneSwitchFlowBlankRequest(@NonNull OneSwitchFlowBlankRequest other) {
        this(
                other.messageContext, other.commandId, other.metadata, other.endpoint, other.meterConfig,
                other.egressEndpoint);
    }

    @Override
    public OneSwitchFlowInstallRequest makeInstallRequest() {
        return new OneSwitchFlowInstallRequest(this);
    }

    @Override
    public OneSwitchFlowRemoveRequest makeRemoveRequest() {
        return new OneSwitchFlowRemoveRequest(this);
    }

    @Override
    public OneSwitchFlowVerifyRequest makeVerifyRequest() {
        return new OneSwitchFlowVerifyRequest(this);
    }

    /**
     * Create "blank" resolver - object capable to create any "real" request type.
     */
    @Builder(builderMethodName = "buildResolver")
    public static BlankResolver makeResolver(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata, FlowEndpoint endpoint,
            MeterConfig meterConfig, FlowEndpoint egressEndpoint) {
        OneSwitchFlowBlankRequest blank = new OneSwitchFlowBlankRequest(
                messageContext, commandId, metadata, endpoint, meterConfig, egressEndpoint);
        return new BlankResolver(blank);
    }

    public static class BlankResolver
            extends FlowSegmentBlankResolver<OneSwitchFlowBlankRequest> {
        BlankResolver(IFlowSegmentBlank<OneSwitchFlowBlankRequest> blank) {
            super(blank);
        }
    }
}
