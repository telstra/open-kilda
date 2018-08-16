/* Copyright 2018 Telstra Open Source
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

package org.openkilda.testing.service.northbound;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;

import java.util.List;
import java.util.stream.Collectors;

public interface NorthboundService {

    HealthCheck getHealthCheck();

    //flows

    FlowPayload getFlow(String flowId);

    FlowPayload addFlow(FlowPayload payload);

    FlowPayload updateFlow(String flowId, FlowPayload payload);

    FlowPayload deleteFlow(String flowId);

    List<FlowPayload> deleteAllFlows();

    FlowPathPayload getFlowPath(String flowId);

    FlowIdStatusPayload getFlowStatus(String flowId);

    List<FlowPayload> getAllFlows();

    List<FlowValidationDto> validateFlow(String flowId);

    //switches

    SwitchFlowEntries getSwitchRules(SwitchId switchId);

    List<Long> deleteSwitchRules(SwitchId switchId);

    RulesSyncResult synchronizeSwitchRules(SwitchId switchId);

    RulesValidationResult validateSwitchRules(SwitchId switchId);

    List<SwitchInfoData> getAllSwitches();

    DeleteMeterResult deleteMeter(SwitchId switchId, Integer meterId);

    //links

    List<IslInfoData> getAllLinks();

    List<LinkPropsDto> getAllLinkProps();

    List<LinkPropsDto> getLinkProps(SwitchId srcSwitch, Integer srcPort, SwitchId dstSwitch, Integer dstPort);

    BatchResults updateLinkProps(List<LinkPropsDto> keys);

    BatchResults deleteLinkProps(List<LinkPropsDto> keys);

    //feature toggles

    FeatureTogglePayload getFeatureToggles();

    FeatureTogglePayload toggleFeature(FeatureTogglePayload request);

    /**
     *  Returns all active links.
     */
    default List<IslInfoData> getActiveLinks() {
        return getAllLinks().stream()
                .filter(sw -> sw.getState() == IslChangeType.DISCOVERED)
                .collect(Collectors.toList());
    }

    /**
     *  Returns all active switches.
     */
    default List<SwitchInfoData> getActiveSwitches() {
        return getAllSwitches().stream()
                .filter(sw -> sw.getState().isActive())
                .collect(Collectors.toList());
    }

}
