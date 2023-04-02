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
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentInstallMultiTableMirrorFlowModFactory;
import org.openkilda.floodlight.command.flow.ingress.of.IngressFlowSegmentInstallSingleTableMirrorFlowModFactory;
import org.openkilda.floodlight.model.FlowSegmentMetadata;
import org.openkilda.floodlight.model.RulesContext;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;
import lombok.Getter;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class IngressMirrorFlowSegmentInstallCommand extends IngressFlowSegmentCommand {
    @JsonCreator
    public IngressMirrorFlowSegmentInstallCommand(
            @JsonProperty("message_context") MessageContext context,
            @JsonProperty("command_id") UUID commandId,
            @JsonProperty("metadata") FlowSegmentMetadata metadata,
            @JsonProperty("endpoint") FlowEndpoint endpoint,
            @JsonProperty("meter_config") MeterConfig meterConfig,
            @JsonProperty("egress_switch") SwitchId egressSwitchId,
            @JsonProperty("isl_port") int islPort,
            @JsonProperty("encapsulation") FlowTransitEncapsulation encapsulation,
            @JsonProperty("rules_context") RulesContext rulesContext,
            @JsonProperty("mirror_config") MirrorConfig mirrorConfig,
            @JsonProperty("stat_vlans") Set<Integer> statVlans) {
        super(context, commandId, metadata, endpoint, meterConfig, egressSwitchId, islPort, encapsulation,
                rulesContext, mirrorConfig, statVlans);
    }

    @Override
    protected void setupFlowModFactory() {
        if (metadata.isMultiTable()) {
            setFlowModFactory(
                    new IngressFlowSegmentInstallMultiTableMirrorFlowModFactory(this, getSw(), getSwitchFeatures()));
        } else {
            setFlowModFactory(
                    new IngressFlowSegmentInstallSingleTableMirrorFlowModFactory(this, getSw(), getSwitchFeatures()));
        }
    }

    @Override
    protected List<Set<SwitchFeature>> getRequiredFeatures() {
        List<Set<SwitchFeature>> required = super.getRequiredFeatures();
        if (encapsulation.getType() == FlowEncapsulationType.VXLAN) {
            required.add(Sets.newHashSet(
                    SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN, SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
        if (metadata.isMultiTable()) {
            required.add(Sets.newHashSet(SwitchFeature.MULTI_TABLE));
        }

        return required;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor) {
        return makeInstallPlan(commandProcessor);
    }

    @Override
    protected SegmentAction getSegmentAction() {
        return SegmentAction.INSTALL;
    }
}
