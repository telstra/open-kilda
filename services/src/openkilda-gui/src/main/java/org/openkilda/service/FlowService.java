package org.openkilda.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.FlowCount;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.utility.CollectionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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


    /**
     * get All Flows.
     *
     * @return SwitchRelationData
     * @throws Exception
     */
    public List<FlowInfo> getAllFlows() {
        return flowsIntegrationService.getFlows();
    }


    /**
     * Gets the flow count.
     *
     * @param switchRelationData the switch relation data
     * @return the flow count
     */
    public Collection<FlowCount> getFlowsCount(final List<Flow> flows) {
        LOGGER.info("Inside ServiceFlowImpl method getFlowsCount");
        Map<FlowCount, FlowCount> infoByFlowInfo = new HashMap<>();
        Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();

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
        LOGGER.info("exit ServiceSwitchImpl method getFlowsCount");
        return infoByFlowInfo.values();
    }

    /**
     * Gets the path link.
     *
     * @param flowid the flowid
     * @return the path link
     * @throws IntegrationException
     */
    public FlowPath getFlowPath(final String flowId) throws IntegrationException {
        return flowsIntegrationService.getFlowPath(flowId);
    }

    /**
     * Gets the all flows list
     *
     * @return the all flow list
     * @throws IntegrationException
     */
    public List<Flow> getAllFlowList() {
        return flowsIntegrationService.getAllFlowList();
    }

    /**
     * Re route Flow by flow id.
     * 
     * @param flowId
     * @return flow path
     */
    public FlowPath rerouteFlow(String flowId) {
        return flowsIntegrationService.rerouteFlow(flowId);
    }

    /**
     * Validate Flow by flow id.
     * 
     * @param flowId
     * @return
     */
    public String validateFlow(String flowId) {
        return flowsIntegrationService.validateFlow(flowId);
    }

    /**
     * Flow by flow id.
     * 
     * @param flowId
     * @return
     */
    public Flow getFlowById(String flowId) {
        return flowsIntegrationService.getFlowById(flowId);
    }


    public FlowStatus getFlowStatusById(String flowId) {
        return flowsIntegrationService.getFlowStatusById(flowId);
    }


	public Flow createFlow(Flow flow) {
		return flowsIntegrationService.createFlow(flow);
	}
}
