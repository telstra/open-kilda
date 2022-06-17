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

package org.openkilda.wfm.topology.utils;

import org.openkilda.floodlight.api.request.factory.EgressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.IngressFlowSegmentRequestFactory;
import org.openkilda.floodlight.api.request.factory.TransitFlowSegmentRequestFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlowSegmentRequestMetaFactory {
    private FlowSegmentMetadata metadata;

    private MessageContext messageContext;

    private FlowTransitEncapsulation encapsulation;

    private SequentialNumberGenerator meterIdGenerator = new SequentialNumberGenerator();

    public FlowSegmentRequestMetaFactory(String flowId, Cookie cookie, int vlanId) {
        this(new FlowSegmentMetadata(flowId, cookie, false), new MessageContext(),
                new FlowTransitEncapsulation(vlanId, FlowEncapsulationType.TRANSIT_VLAN));
    }

    public FlowSegmentRequestMetaFactory(
            FlowSegmentMetadata metadata, MessageContext messageContext, FlowTransitEncapsulation encapsulation) {
        this.metadata = metadata;
        this.messageContext = messageContext;
        this.encapsulation = encapsulation;
    }

    /**
     * Produce {@link IngressFlowSegmentRequestFactory}.
     */
    public IngressFlowSegmentRequestFactory produceIngressSegmentFactory(
            Long bandwidth, FlowEndpoint endpoint, int islPort, SwitchId egressSwitchId) {
        MeterConfig meterConfig = null;
        if (bandwidth != null && 0 < bandwidth) {
            MeterId meterId = new MeterId(meterIdGenerator.generateLong());
            meterConfig = new MeterConfig(meterId, bandwidth);
        }
        return new IngressFlowSegmentRequestFactory(
                messageContext, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation,
                new RulesContext(), null);
    }

    /**
     * Produce {@link TransitFlowSegmentRequestFactory}.
     */
    public TransitFlowSegmentRequestFactory produceTransitSegmentFactory(
            SwitchId switchId, int ingressIslPort, int egressIslPort) {
        return new TransitFlowSegmentRequestFactory(
                messageContext, switchId, metadata, ingressIslPort, egressIslPort, encapsulation);
    }

    /**
     * Produce {@link EgressFlowSegmentRequestFactory}.
     */
    public EgressFlowSegmentRequestFactory produceEgressSegmentFactory(
            FlowEndpoint endpoint, int islPort, FlowEndpoint ingressEndpoint) {
        return new EgressFlowSegmentRequestFactory(
                messageContext, metadata, endpoint, ingressEndpoint, islPort, encapsulation, null);
    }

    public String getFlowId() {
        return metadata.getFlowId();
    }
}
