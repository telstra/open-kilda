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

package org.openkilda.floodlight.command.flow.ingress.of;

import org.openkilda.floodlight.command.flow.ingress.OneSwitchFlowCommand;
import org.openkilda.floodlight.utils.OfAdapter;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

abstract class OneSwitchFlowInstallFlowModFactory extends IngressInstallFlowModFactory {
    private final OneSwitchFlowCommand command;

    public OneSwitchFlowInstallFlowModFactory(
            OfFlowModBuilderFactory flowModBuilderFactory, OneSwitchFlowCommand command, IOFSwitch sw,
            Set<SwitchFeature> features) {
        super(flowModBuilderFactory, command, sw, features);
        this.command = command;
    }

    @Override
    protected List<OFAction> makeTransformActions() {
        List<Integer> currentVlanStack = new ArrayList<>();
        FlowEndpoint endpoint = command.getEndpoint();
        if (FlowEndpoint.isVlanIdSet(endpoint.getVlanId())) {
            currentVlanStack.add(endpoint.getVlanId());
        }

        List<Integer> desiredVlanStack = new ArrayList<>();
        FlowEndpoint egressEndpoint = command.getEgressEndpoint();
        if (FlowEndpoint.isVlanIdSet(egressEndpoint.getVlanId())) {
            desiredVlanStack.add(egressEndpoint.getVlanId());
        }

        return OfAdapter.INSTANCE.makeVlanReplaceActions(of, currentVlanStack, desiredVlanStack);
    }

    @Override
    protected OFAction makeOutputAction() {
        FlowEndpoint endpoint = command.getEndpoint();
        FlowEndpoint egressEndpoint = command.getEgressEndpoint();
        if (endpoint.getPortNumber().equals(egressEndpoint.getPortNumber())) {
            return super.makeOutputAction(OFPort.IN_PORT);
        }
        return super.makeOutputAction(OFPort.of(egressEndpoint.getPortNumber()));
    }
}
