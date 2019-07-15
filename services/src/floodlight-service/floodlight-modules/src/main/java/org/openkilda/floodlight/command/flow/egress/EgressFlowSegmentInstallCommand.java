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

package org.openkilda.floodlight.command.flow.egress;

import org.openkilda.floodlight.error.NotImplementedEncapsulationException;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
public class EgressFlowSegmentInstallCommand extends EgressFlowSegmentBlankCommand {
    @JsonCreator
    public EgressFlowSegmentInstallCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("ingress_endpoint") FlowEndpoint ingressEndpoint,
            @JsonProperty("islPort") Integer islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation) {
        super(context, commandId, metadata, endpoint, ingressEndpoint, islPort, encapsulation);
    }

    @Override
    protected List<OFInstruction> makeEgressModMessageInstructions(OFFactory of) {
        List<OFAction> applyActions = new ArrayList<>(makeTransformActions(of));
        applyActions.add(of.actions().buildOutput()
                                 .setPort(OFPort.of(endpoint.getPortNumber()))
                                 .build());

        return ImmutableList.of(of.instructions().applyActions(applyActions));
    }

    private List<OFAction> makeTransformActions(OFFactory of) {
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                return makeVlanTransformActions(of);
            case VXLAN:
                return makeVxLanTransformActions(of);
            default:
                throw new NotImplementedEncapsulationException(
                        getClass(), encapsulation.getType(), switchId, metadata.getFlowId());
        }
    }

    private List<OFAction> makeVlanTransformActions(OFFactory of) {
        List<Integer> currentVlanStack = Collections.singletonList(encapsulation.getId());
        return makeEndpointReEncoding(of, currentVlanStack);
    }

    private List<OFAction> makeVxLanTransformActions(OFFactory of) {
        List<OFAction> actions = new ArrayList<>();
        actions.add(of.actions().noviflowPopVxlanTunnel());

        List<Integer> currentVlanStack = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(ingressEndpoint.getVlanId())) {
            currentVlanStack.add(ingressEndpoint.getVlanId());
        }
        actions.addAll(makeEndpointReEncoding(of, currentVlanStack));

        return actions;
    }

    private List<OFAction> makeEndpointReEncoding(OFFactory of, List<Integer> currentVlanStack) {
        List<Integer> desiredVlanStack = new ArrayList<>();
        if (FlowEndpoint.isVlanIdSet(endpoint.getVlanId())) {
            desiredVlanStack.add(endpoint.getVlanId());
        }
        return OfAdapter.INSTANCE.makeVlanReplaceActions(of, currentVlanStack, desiredVlanStack);
    }

    @Override
    protected OFFlowMod.Builder makeFlowModBuilder(OFFactory of) {
        return makeFlowAddBuilder(of);
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
