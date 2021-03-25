/* Copyright 2021 Telstra Open Source
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

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentInstallMultiTableFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentInstallSingleTableFlowModFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import lombok.NonNull;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class IngressServer42FlowInstallCommand extends IngressServer42FlowCommand {
    public IngressServer42FlowInstallCommand(
            MessageContext messageContext, UUID commandId, FlowSegmentMetadata metadata, SwitchId switchId,
            int customerPort, int outerVlanId, int innerVlanId, SwitchId egressSwitchId, int islPort,
            @NonNull FlowTransitEncapsulation encapsulation, int server42Port, MacAddress server42MacAddress) {

        super(messageContext, commandId, metadata, new FlowEndpoint(switchId, customerPort, outerVlanId, innerVlanId),
                egressSwitchId, islPort, encapsulation, RulesContext.builder()
                        .server42MacAddress(server42MacAddress)
                        .server42Port(server42Port)
                        .installServer42IngressRule(true)
                        .build());
    }

    @Override
    protected void setupFlowModFactory() {
        if (metadata.isMultiTable()) {
            setFlowModFactory(
                    new IngressFlowSegmentInstallMultiTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        } else {
            setFlowModFactory(
                    new IngressFlowSegmentInstallSingleTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        }
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        return makeInstallPlan(commandProcessor);
    }

    @Override
    protected List<OFFlowMod> makeFlowModMessages(MeterId effectiveMeterId) {
        return makeServer42IngressFlowModMessages();
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
