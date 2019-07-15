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

package org.openkilda.floodlight.command.flow.ingress;

import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterConfig;

import lombok.Getter;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
abstract class OneSwitchFlowBlankCommand extends IngressFlowSegmentCommand {
    protected final FlowEndpoint egressEndpoint;

    OneSwitchFlowBlankCommand(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata,
            FlowEndpoint endpoint, MeterConfig meterConfig, @NonNull FlowEndpoint egressEndpoint) {
        super(
                messageContext, endpoint.getDatapath(), commandId, metadata, endpoint, meterConfig,
                egressEndpoint.getDatapath());
        this.egressEndpoint = egressEndpoint;
    }

    @Override
    protected void validate() {
        super.validate();

        if (! getSwitchId().equals(egressEndpoint.getDatapath())) {
            throw new IllegalArgumentException(String.format(
                    "Ingress(%s) and egress(%s) switches must match in %s",
                    getSwitchId(), egressEndpoint.getDatapath(), getClass().getName()));
        }
    }

    @Override
    protected List<OFAction> makeTransformActions(OFFactory of) {
        List<Integer> currentVlanStack = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getVlanId())) {
            currentVlanStack.add(endpoint.getVlanId());
        }

        List<Integer> desiredVlanStack = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(egressEndpoint.getVlanId())) {
            desiredVlanStack.add(egressEndpoint.getVlanId());
        }

        return OfAdapter.INSTANCE.makeVlanReplaceActions(of, currentVlanStack, desiredVlanStack);
    }

    @Override
    protected OFAction makeOutputAction(OFFactory of) {
        if (endpoint.getPortNumber().equals(egressEndpoint.getPortNumber())) {
            return super.makeOutputAction(of, OFPort.IN_PORT);
        }
        return super.makeOutputAction(of, OFPort.of(egressEndpoint.getPortNumber()));
    }

    public String toString() {
        return String.format(
                "<one-switch-flow-%s{id=%s, metadata=%s, endpoint=%s, egressEndpoint=%s}>",
                getSegmentAction(), commandId, metadata, endpoint, egressEndpoint);
    }

    protected abstract SegmentAction getSegmentAction();
}
