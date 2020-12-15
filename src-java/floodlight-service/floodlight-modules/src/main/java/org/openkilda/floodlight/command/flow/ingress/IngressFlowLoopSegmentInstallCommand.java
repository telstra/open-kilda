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

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowLoopInstallMultiTableFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowLoopInstallSingleTableFlowModFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchFeature;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFlowMod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class IngressFlowLoopSegmentInstallCommand extends IngressFlowLoopCommand {
    @JsonCreator
    public IngressFlowLoopSegmentInstallCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint) {
        super(context, commandId, metadata, endpoint);
    }

    @Override
    protected Set<SwitchFeature> getRequiredFeatures() {
        Set<SwitchFeature> required = super.getRequiredFeatures();
        if (metadata.isMultiTable()) {
            required.add(SwitchFeature.MULTI_TABLE);
        }

        return required;
    }

    @Override
    protected void setupFlowModFactory() {
        if (metadata.isMultiTable()) {
            setFlowModFactory(
                    new IngressFlowLoopInstallMultiTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        } else {
            setFlowModFactory(
                    new IngressFlowLoopInstallSingleTableFlowModFactory(this, getSw(), getSwitchFeatures()));
        }
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        return makeInstallPlan(commandProcessor);
    }

    @Override
    protected List<OFFlowMod> makeFlowModMessages(MeterId effectiveMeterId) {
        return makeIngressLoopFlowModMessages();
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
