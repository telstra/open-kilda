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

package org.openkilda.integration.converter;

import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowEndpoint;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.dto.InventoryFlow;
import org.openkilda.model.FlowBandwidth;
import org.openkilda.model.FlowDiscrepancy;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowState;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Class FlowConverter.
 */
@Component
public class FlowConverter {
    
    /** The switch integration service. */
    @Autowired
    SwitchIntegrationService switchIntegrationService;

    /**
     * To flows info.
     *
     * @param flows the flows
     * @return the list
     */
    public List<FlowInfo> toFlowsInfo(final List<Flow> flows) { 
        if (!CollectionUtil.isEmpty(flows)) {
            final List<FlowInfo> flowsInfo = new ArrayList<>();
            final Map<String, String> csNames = switchIntegrationService.getSwitchNames();
            flows.forEach(flow -> {
                flowsInfo.add(toFlowInfo(flow, csNames));
            });
            return flowsInfo;
        }
        return null;
    }

    /**
     * To flow info.
     *
     * @param flow the flow
     * @param csNames the cs names
     * @return the flow info
     */
    public FlowInfo toFlowInfo(final Flow flow, Map<String, String> csNames) {
        FlowInfo flowInfo = new FlowInfo();
        flowInfo.setFlowid(flow.getId());
        flowInfo.setMaximumBandwidth(flow.getMaximumBandwidth());
        flowInfo.setAllocateProtectedPath(flow.isAllocateProtectedPath());
        flowInfo.setDescription(flow.getDescription());
        flowInfo.setStatus(flow.getStatus().toUpperCase());
        flowInfo.setDiverseFlowid(flow.getDiverseFlowId());
        flowInfo.setDiverseWith(flow.getDiverseWith());
        flowInfo.setControllerFlow(true);
        FlowEndpoint source = flow.getSource();
        if (source != null) {
            String switchName = switchIntegrationService.customSwitchName(csNames, source.getSwitchId());
            flowInfo.setSourceSwitchName(switchName);
            flowInfo.setSourceSwitch(source.getSwitchId());
            flowInfo.setSrcPort(source.getPortId());
            flowInfo.setSrcVlan(source.getVlanId());
        }
        FlowEndpoint destination = flow.getDestination();
        if (destination != null) {
            String switchName = switchIntegrationService.customSwitchName(csNames, destination.getSwitchId());
            flowInfo.setTargetSwitchName(switchName);
            flowInfo.setTargetSwitch(destination.getSwitchId());
            flowInfo.setDstPort(destination.getPortId());
            flowInfo.setDstVlan(destination.getVlanId());
        }
        return flowInfo;
    }
    
    /**
     * To flow info.
     *
     * @param flowInfo the flow info
     * @param inventoryFlow the inventory flow
     * @param csNames the cs names
     * @return the flow info
     */
    public FlowInfo toFlowInfo(final FlowInfo flowInfo, final InventoryFlow inventoryFlow,
            final Map<String, String> csNames) {

        FlowDiscrepancy discrepancy = new FlowDiscrepancy();
        discrepancy.setControllerDiscrepancy(true);
        discrepancy.setStatus(true);
        discrepancy.setBandwidth(true);
        
        FlowBandwidth flowBandwidth = new FlowBandwidth();
        flowBandwidth.setControllerBandwidth(0);
        flowBandwidth.setInventoryBandwidth(inventoryFlow.getMaximumBandwidth());
        discrepancy.setBandwidthValue(flowBandwidth);
        
        FlowState flowState = new FlowState();
        flowState.setControllerState(null);
        flowState.setInventoryState(inventoryFlow.getState());
        discrepancy.setStatusValue(flowState);
        
        flowInfo.setFlowid(inventoryFlow.getId());
        flowInfo.setDiscrepancy(discrepancy);
        if (!StringUtil.isNullOrEmpty(inventoryFlow.getSource().getId())) {
            flowInfo.setSourceSwitch(inventoryFlow.getSource().getId());
            flowInfo.setSourceSwitchName(
                    switchIntegrationService.customSwitchName(csNames, inventoryFlow.getSource().getId()));
        }
        if (inventoryFlow.getSource().getPortId() != null) {
            flowInfo.setSrcPort(inventoryFlow.getSource().getPortId());
        }
        try {
            flowInfo.setSrcVlan(Integer.parseInt(inventoryFlow.getSource().getVlanId()));
        } catch (NumberFormatException numberFormatException) {
            inventoryFlow.getSource().setVlanId(null);
        }
        
        if (!StringUtil.isNullOrEmpty(inventoryFlow.getDestination().getId())) {
            flowInfo.setTargetSwitch(inventoryFlow.getDestination().getId());
            flowInfo.setTargetSwitchName(
                    switchIntegrationService.customSwitchName(csNames, inventoryFlow.getDestination().getId()));
        }
        if (inventoryFlow.getDestination().getPortId() != null) {
            flowInfo.setDstPort(inventoryFlow.getDestination().getPortId());
        }
        try {
            flowInfo.setDstVlan(Integer.parseInt(inventoryFlow.getDestination().getVlanId()));
        } catch (NumberFormatException numberFormatException) {
            inventoryFlow.getDestination().setVlanId(null);
        }

        flowInfo.setDescription(inventoryFlow.getDescription());
        flowInfo.setMaximumBandwidth(inventoryFlow.getMaximumBandwidth());
        flowInfo.setIgnoreBandwidth(inventoryFlow.getIgnoreBandwidth());
        flowInfo.setState(inventoryFlow.getState());
        return flowInfo;
    }

    /**
     * To flow with switch names.
     *
     * @param flow the flow
     * @return the flow
     */
    public Flow toFlowWithSwitchNames(final Flow flow) {
        final Map<String, String> csNames = switchIntegrationService.getSwitchNames();
        FlowEndpoint source = flow.getSource();
        if (source != null) {
            String switchName = switchIntegrationService.customSwitchName(csNames, source.getSwitchId());
            source.setSwitchName(switchName);
        }
        FlowEndpoint destination = flow.getDestination();
        if (destination != null) {
            String switchName = switchIntegrationService.customSwitchName(csNames, destination.getSwitchId());
            destination.setSwitchName(switchName);
        }
        return flow;
    }
}
