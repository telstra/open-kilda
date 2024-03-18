/* Copyright 2023 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.FlowSubType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.generator.flow.Server42IngressRuleGenerator;
import org.openkilda.rulemanager.utils.RoutingMetadata.HaSubFlowType;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuperBuilder
@Getter
@Slf4j
public class SharedYServer42IngressForwardHaRuleGenerator extends Server42IngressRuleGenerator {

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (switchProperties == null || !switchProperties.isServer42FlowRtt()
                || flow != null || haFlow == null) {
            return Collections.emptyList();
        }

        List<FlowPath> flowPaths = haFlow.getForwardPath().getSubPaths();
        if (flowPaths.size() > 2) {
            return Collections.emptyList();
        }

        Map<FlowSubType, HaSubFlowType> cookieFlowSubTypeToMetadataHaSubFlowType = ImmutableMap.of(
                FlowSubType.HA_SUB_FLOW_1, HaSubFlowType.HA_SUB_FLOW_1,
                FlowSubType.HA_SUB_FLOW_2, HaSubFlowType.HA_SUB_FLOW_2
        );

        FlowEndpoint ingressEndpoint = getIngressEndpoint(sw.getSwitchId());
        List<SpeakerData> result = new ArrayList<>();
        if (needToBuildServer42PreIngressRule(ingressEndpoint)) {
            result.add(buildServer42PreIngressCommand(sw, ingressEndpoint));
        }
        flowPaths.stream().filter(flowPath -> !flowPath.isOneSwitchPath()).forEach(flowPath -> {
            HaSubFlowType haSubFlowType =
                    cookieFlowSubTypeToMetadataHaSubFlowType.get(flowPath.getCookie().getFlowSubType());
            result.add(buildServer42IngressCommand(sw, ingressEndpoint, flowPath, haSubFlowType));

            if (needToBuildServer42InputRule(ingressEndpoint)) {
                result.add(buildServer42InputCommand(sw, ingressEndpoint.getPortNumber(), flowPath, haSubFlowType));
            }
        });
        return result;
    }
}
