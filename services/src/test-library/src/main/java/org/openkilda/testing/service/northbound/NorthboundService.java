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

import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.InstallRulesAction;
import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.switches.PortDescription;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.northbound.dto.flows.PingInput;
import org.openkilda.northbound.dto.flows.PingOutput;
import org.openkilda.northbound.dto.links.LinkPropsDto;
import org.openkilda.northbound.dto.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.switches.PortDto;
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

    PingOutput pingFlow(String flowId, PingInput pingInput);

    FlowReroutePayload rerouteFlow(String flowId);

    //switches

    SwitchFlowEntries getSwitchRules(SwitchId switchId);

    List<Long> installSwitchRules(SwitchId switchId, InstallRulesAction installAction);

    List<Long> deleteSwitchRules(SwitchId switchId, DeleteRulesAction deleteAction);

    RulesSyncResult synchronizeSwitchRules(SwitchId switchId);

    RulesValidationResult validateSwitchRules(SwitchId switchId);

    List<SwitchInfoData> getAllSwitches();

    DeleteMeterResult deleteMeter(SwitchId switchId, Integer meterId);

    PortDto configurePort(SwitchId switchId, Integer portNo, Object config);

    PortDto portDown(SwitchId switchId, Integer portNo);

    PortDto portUp(SwitchId switchId, Integer portNo);

    List<PortDescription> getPorts(SwitchId switchId);

    PortDescription getPort(SwitchId switchId, Integer portNo);

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
     * Returns all active links.
     */
    default List<IslInfoData> getActiveLinks() {
        return getAllLinks().stream()
                .filter(sw -> sw.getState() == IslChangeType.DISCOVERED)
                .collect(Collectors.toList());
    }

    /**
     * Returns all active switches.
     */
    default List<SwitchInfoData> getActiveSwitches() {
        return getAllSwitches().stream()
                .filter(sw -> sw.getState().isActive())
                .collect(Collectors.toList());
    }

}
