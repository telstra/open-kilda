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

package org.openkilda.service;

import org.openkilda.constants.IConstants;
import org.openkilda.integration.converter.FlowConverter;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.FlowStoreService;
import org.openkilda.integration.source.store.dto.InventoryFlow;
import org.openkilda.log.ActivityLogger;
import org.openkilda.log.constants.ActivityType;
import org.openkilda.model.FlowBandwidth;
import org.openkilda.model.FlowCount;
import org.openkilda.model.FlowDiscrepancy;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowState;
import org.openkilda.model.Status;
import org.openkilda.store.model.LinkStoreConfigDto;
import org.openkilda.store.service.StoreService;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.StringUtil;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Class ServiceFlowImpl.
 *
 * @author Gaurav Chugh
 */

@Service
public class FlowService {

    private static final Logger LOGGER = Logger.getLogger(FlowService.class);

    @Autowired
    private FlowsIntegrationService flowsIntegrationService;

    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    @Autowired
    private UserService userService;

    @Autowired
    private ActivityLogger activityLogger;

    @Autowired
    private FlowStoreService flowStoreService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private FlowConverter flowConverter;

    /**
     * get All Flows.
     *
     * @return SwitchRelationData
     */
    public List<FlowInfo> getAllFlows(List<String> statuses, boolean controller) {
        List<FlowInfo> flows = new ArrayList<FlowInfo>();
        if (!CollectionUtil.isEmpty(statuses)) {
            statuses = statuses.stream().map((status) -> status.toLowerCase()).collect(Collectors.toList());
        }
        if (CollectionUtil.isEmpty(statuses) || statuses.contains("active")) {
            flows = flowsIntegrationService.getFlows();
            if (flows == null) {
                flows = new ArrayList<FlowInfo>();
            }
        }
        if (!controller) {
            if (storeService.getLinkStoreConfig().getUrls().size() > 0) {
                try {
                    UserInfo userInfo = userService.getLoggedInUserInfo();
                    if (userInfo.getPermissions().contains(IConstants.Permission.FW_FLOW_INVENTORY)) {
                        List<InventoryFlow> inventoryFlows = new ArrayList<InventoryFlow>();
                        String status = "";
                        for (String statusObj : statuses) {
                            if (StringUtil.isNullOrEmpty(status)) {
                                status += statusObj;
                            } else {
                                status += "," + statusObj;
                            }
                        }
                        inventoryFlows = flowStoreService.getFlowsWithParams(status);
                        processInventoryFlow(flows, inventoryFlows);
                    }
                } catch (Exception ex) {
                    LOGGER.error("Error occurred while retrieving flows from store", ex);
                }
            }
        }
        return flows;
    }

    /**
     * Gets the flow count.
     *
     * @param flows
     *            the flows
     * @return the flow count
     */
    public Collection<FlowCount> getFlowsCount(final List<Flow> flows) {
        Map<FlowCount, FlowCount> infoByFlowInfo = new HashMap<>();
        Map<String, String> csNames = switchIntegrationService.getSwitchNames();

        if (!CollectionUtil.isEmpty(flows)) {
            flows.forEach((flow) -> {
                FlowCount flowInfo = new FlowCount();
                if (flow.getSource() != null) {
                    flowInfo.setSrcSwitch(flow.getSource().getSwitchId());
                    String srcSwitchName = switchIntegrationService.customSwitchName(csNames,
                            flow.getSource().getSwitchId());
                    flowInfo.setSrcSwitchName(srcSwitchName);
                }
                if (flow.getDestination() != null) {
                    flowInfo.setDstSwitch(flow.getDestination().getSwitchId());
                    String dstSwitchName = switchIntegrationService.customSwitchName(csNames,
                            flow.getDestination().getSwitchId());
                    flowInfo.setDstSwitchName(dstSwitchName);
                }
                flowInfo.setFlowCount(1);

                if (infoByFlowInfo.containsKey(flowInfo)) {
                    infoByFlowInfo.get(flowInfo).incrementFlowCount();
                } else {
                    infoByFlowInfo.put(flowInfo, flowInfo);
                }
            });
        }
        return infoByFlowInfo.values();
    }

    /**
     * Gets the path link.
     *
     * @param flowId
     *            the flow id
     * @return the path link
     */
    public FlowPayload getFlowPath(final String flowId) throws IntegrationException {
        return flowsIntegrationService.getFlowPath(flowId);
    }

    /**
     * Gets the all flows list.
     *
     * @return the all flow list
     */
    public List<Flow> getAllFlowList() {
        return flowsIntegrationService.getAllFlowList();
    }

    /**
     * Re route Flow by flow id.
     *
     * @param flowId
     *            the flow id
     * @return flow path
     */
    public FlowPath rerouteFlow(String flowId) {
        return flowsIntegrationService.rerouteFlow(flowId);
    }

    /**
     * Validate Flow.
     *
     * @param flowId
     *            the flow id
     * @return the string
     */
    public String validateFlow(String flowId) {
        return flowsIntegrationService.validateFlow(flowId);
    }

    /**
     * Flow by flow id.
     *
     * @param flowId
     *            the flow id
     * @return the flow by id
     */
    public FlowInfo getFlowById(String flowId, boolean controller) {
        FlowInfo flowInfo = new FlowInfo();
        Flow flow = null;
        try {
            flow = flowsIntegrationService.getFlowById(flowId);
        } catch (Exception ex) {
            LOGGER.error("Error occurred while retrieving flows from controller", ex);
        }
        Map<String, String> csNames = switchIntegrationService.getSwitchNames();
        if (flow != null) {
            flowInfo = flowConverter.toFlowInfo(flow, csNames);
        }
        UserInfo userInfo = userService.getLoggedInUserInfo();
        if (!controller && userInfo.getPermissions().contains(IConstants.Permission.FW_FLOW_INVENTORY)) {
            if (storeService.getLinkStoreConfig().getUrls().size() > 0) {
                try {
                    InventoryFlow inventoryFlow = flowStoreService.getFlowById(flowId);
                    if (flow != null && inventoryFlow != null) {
                        flowInfo.setState(inventoryFlow.getState());
                        flowInfo.setIgnoreBandwidth(inventoryFlow.getIgnoreBandwidth());
                        FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                        discrepancy.setControllerDiscrepancy(false);
                        if (flowInfo.getMaximumBandwidth() != (inventoryFlow.getMaximumBandwidth() == null ? 0
                                : inventoryFlow.getMaximumBandwidth())) {
                            discrepancy.setBandwidth(true);

                            FlowBandwidth flowBandwidth = new FlowBandwidth();
                            flowBandwidth.setControllerBandwidth(flow.getMaximumBandwidth());
                            flowBandwidth.setInventoryBandwidth(inventoryFlow.getMaximumBandwidth());
                            discrepancy.setBandwidthValue(flowBandwidth);
                        }
                        if (("UP".equalsIgnoreCase(flowInfo.getStatus())
                                && !"ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))
                                || ("DOWN".equalsIgnoreCase(flowInfo.getStatus())
                                    && "ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))) {
                            discrepancy.setStatus(true);

                            FlowState flowState = new FlowState();
                            flowState.setControllerState(flow.getStatus());
                            flowState.setInventoryState(inventoryFlow.getState());
                            discrepancy.setStatusValue(flowState);
                        }
                        flowInfo.setInventoryFlow(true);
                        flowInfo.setDiscrepancy(discrepancy);
                    } else if (inventoryFlow == null && flow != null) {
                        FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                        discrepancy.setInventoryDiscrepancy(true);
                        discrepancy.setControllerDiscrepancy(false);
                        discrepancy.setStatus(true);
                        discrepancy.setBandwidth(true);

                        FlowBandwidth flowBandwidth = new FlowBandwidth();
                        flowBandwidth.setControllerBandwidth(flow.getMaximumBandwidth());
                        flowBandwidth.setInventoryBandwidth(0);
                        discrepancy.setBandwidthValue(flowBandwidth);

                        FlowState flowState = new FlowState();
                        flowState.setControllerState(flow.getStatus());
                        flowState.setInventoryState(null);
                        discrepancy.setStatusValue(flowState);

                        flowInfo.setDiscrepancy(discrepancy);
                    } else {
                        flowConverter.toFlowInfo(flowInfo, inventoryFlow, csNames);
                    }
                } catch (Exception ex) {
                    LOGGER.error("Error occurred while retrieving flows from store", ex);
                }
            }
        }
        return flowInfo;
    }

    /**
     * Gets the flow status by id.
     *
     * @param flowId
     *            the flow id
     * @return the flow status by id
     */
    public FlowStatus getFlowStatusById(String flowId) {
        return flowsIntegrationService.getFlowStatusById(flowId);
    }

    /**
     * Creates the flow.
     *
     * @param flow
     *            the flow
     * @return the flow
     */
    public Flow createFlow(Flow flow) {
        flow = flowsIntegrationService.createFlow(flow);
        activityLogger.log(ActivityType.CREATE_FLOW, flow.getId());
        return flow;
    }

    /**
     * Update flow.
     *
     * @param flowId
     *            the flow id
     * @param flow
     *            the flow
     * @return the flow
     */
    public Flow updateFlow(String flowId, Flow flow) {
        activityLogger.log(ActivityType.UPDATE_FLOW, flow.getId());
        flow = flowsIntegrationService.updateFlow(flowId, flow);
        return flow;
    }

    /**
     * Delete flow.
     *
     * @param flowId
     *            the flow id
     * @param userInfo
     *            the user info
     * @return the flow
     */
    public Flow deleteFlow(String flowId, UserInfo userInfo) {
        if (userService.validateOtp(userInfo.getUserId(), userInfo.getCode())) {
            Flow flow = flowsIntegrationService.deleteFlow(flowId);
            activityLogger.log(ActivityType.DELETE_FLOW, flow.getId());
            return flow;
        } else {
            return null;
        }
    }

    /**
     * Re sync flow.
     * 
     * @param flowId
     *            the flow id
     * 
     * @return
     */
    public String resyncFlow(String flowId) {
        activityLogger.log(ActivityType.RESYNC_FLOW, flowId);
        return flowsIntegrationService.resyncFlow(flowId);
    }
    
    /**
     * Flow ping.
     *
     * @param flowId the flow id
     * @param flow the flow
     * @return the string
     */
    public String flowPing(String flowId, Flow flow) {
        return flowsIntegrationService.flowPing(flow, flowId);
    }

    /**
     * Process inventory flow.
     *
     * @param flows
     *            the flows
     * @param inventoryFlows
     *            the inventory flows
     */
    private void processInventoryFlow(final List<FlowInfo> flows, final List<InventoryFlow> inventoryFlows) {
        List<FlowInfo> discrepancyFlow = new ArrayList<FlowInfo>();
        final Map<String, String> csNames = switchIntegrationService.getSwitchNames();
        for (InventoryFlow inventoryFlow : inventoryFlows) {
            int index = -1;
            for (FlowInfo flow : flows) {
                if (flow.getFlowid().equals(inventoryFlow.getId())) {
                    index = flows.indexOf(flow);
                    break;
                }
            }
            if (index >= 0) {
                FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                discrepancy.setControllerDiscrepancy(false);
                if (flows.get(index).getMaximumBandwidth() != inventoryFlow.getMaximumBandwidth()) {
                    discrepancy.setInventoryDiscrepancy(true);
                    discrepancy.setBandwidth(true);
                    FlowBandwidth flowBandwidth = new FlowBandwidth();
                    flowBandwidth.setControllerBandwidth(flows.get(index).getMaximumBandwidth());
                    flowBandwidth.setInventoryBandwidth(inventoryFlow.getMaximumBandwidth());
                    discrepancy.setBandwidthValue(flowBandwidth);

                }
                if (("UP".equalsIgnoreCase(flows.get(index).getStatus())
                        && !"ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))
                        || ("DOWN".equalsIgnoreCase(flows.get(index).getStatus())
                                && "ACTIVE".equalsIgnoreCase(inventoryFlow.getState()))) {
                    discrepancy.setInventoryDiscrepancy(true);
                    discrepancy.setStatus(true);

                    FlowState flowState = new FlowState();
                    flowState.setControllerState(flows.get(index).getStatus());
                    flowState.setInventoryState(inventoryFlow.getState());
                    discrepancy.setStatusValue(flowState);
                }
                flows.get(index).setDiscrepancy(discrepancy);
                flows.get(index).setState(inventoryFlow.getState());
                flows.get(index).setIgnoreBandwidth(inventoryFlow.getIgnoreBandwidth());
                flows.get(index).setInventoryFlow(true);
            } else {
                FlowInfo flowObj = new FlowInfo();
                flowConverter.toFlowInfo(flowObj, inventoryFlow, csNames);
                flowObj.setInventoryFlow(true);
                discrepancyFlow.add(flowObj);
            }
        }

        for (FlowInfo flow : flows) {
            boolean flag = false;
            for (InventoryFlow inventoryFlow : inventoryFlows) {
                if (flow.getFlowid().equals(inventoryFlow.getId())) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                FlowDiscrepancy discrepancy = new FlowDiscrepancy();
                discrepancy.setInventoryDiscrepancy(true);
                discrepancy.setControllerDiscrepancy(false);
                discrepancy.setStatus(true);
                discrepancy.setBandwidth(true);

                FlowBandwidth flowBandwidth = new FlowBandwidth();
                flowBandwidth.setControllerBandwidth(flow.getMaximumBandwidth());
                flowBandwidth.setInventoryBandwidth(0);
                discrepancy.setBandwidthValue(flowBandwidth);

                FlowState flowState = new FlowState();
                flowState.setControllerState(flow.getStatus());
                flowState.setInventoryState(null);
                discrepancy.setStatusValue(flowState);

                flow.setDiscrepancy(discrepancy);
            }
            flow.setControllerFlow(true);
        }
        flows.addAll(discrepancyFlow);
    }

    /**
     * Gets the all status list.
     *
     * @return the all status list
     */
    public Set<String> getAllStatus() {
        LinkStoreConfigDto linkStoreConfigDto = storeService.getLinkStoreConfig();
        boolean isLinkStoreConfig = linkStoreConfigDto.getUrls().isEmpty();
        Status status = Status.INSTANCE;
        if (!isLinkStoreConfig) {
            if (CollectionUtil.isEmpty(status.getStatuses())) {
                status.setStatuses(new HashSet<String>(flowStoreService.getAllStatus()));
            }
        } else {
            LOGGER.info("Link store is not configured. ");
        }
        return status.getStatuses() != null ? status.getStatuses() : new HashSet<String>();
    }
}
