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

import org.openkilda.floodlight.command.flow.ingress.IngressFlowSegmentBase;
import org.openkilda.floodlight.utils.OfFlowModBuilderFactory;
import org.openkilda.floodlight.utils.metadata.RoutingMetadata;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class IngressRemoveFlowModFactory extends IngressFlowModFactory {
    public IngressRemoveFlowModFactory(
            OfFlowModBuilderFactory flowModBuilderFactory, IngressFlowSegmentBase command, IOFSwitch sw,
            Set<SwitchFeature> features) {
        super(flowModBuilderFactory, command, sw, features);
    }

    @Override
    protected List<OFInstruction> makeForwardMessageInstructions(MeterId effectiveMeterId, List<Integer> vlanStack) {
        return Collections.emptyList();
    }

    @Override
    protected List<OFInstruction> makeIngressFlowLoopInstructions(FlowEndpoint endpoint) {
        return Collections.emptyList();
    }

    @Override
    protected List<OFInstruction> makeOuterVlanMatchInstructions() {
        return Collections.emptyList();
    }

    @Override
    protected List<OFInstruction> makeCustomerPortSharedCatchInstructions() {
        return Collections.emptyList();
    }

    @Override
    protected List<OFInstruction> makeConnectedDevicesMatchInstructions(RoutingMetadata metadata) {
        return Collections.emptyList();
    }

    @Override
    protected List<OFInstruction> makeServer42IngressFlowMessageInstructions(List<Integer> vlanStack) {
        return Collections.emptyList();
    }
}
