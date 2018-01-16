package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openkilda.integration.model.response.FlowStatus;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.response.FlowCount;
import org.openkilda.model.response.FlowPath;
import org.openkilda.service.helper.FlowHelper;
import org.openkilda.utility.CollectionUtil;

/**
 * The Class ServiceFlowImpl.
 *
 * @author Gaurav Chugh
 */
@Service
public class ServiceFlow {

    private static final Logger LOGGER = Logger.getLogger(ServiceFlow.class);

    /** The flow data util. */
    @Autowired
    private FlowHelper flowDataUtil;

    /** The flows integration service. */
    @Autowired
    private FlowsIntegrationService flowsIntegrationService;


    /**
     * get All Flows.
     *
     * @return SwitchRelationData
     */
    public List<FlowInfo> getAllFlows() {
        List<FlowInfo> flowInfos = flowsIntegrationService.getFlows();

        if (!CollectionUtil.isEmpty(flowInfos)) {
            flowInfos.forEach(flowInfo -> {
                String status = "";
                FlowStatus flowStatusResponse =
                        flowsIntegrationService.getFlowStatus(flowInfo.getFlowid());
                if (flowStatusResponse != null) {
                    status = flowStatusResponse.getStatus();
                }
                flowInfo.setStatus(status);
            });
        }
        return flowInfos;
    }


    /**
     * Gets the flow count.
     *
     * @param switchRelationData the switch relation data
     * @return the flow count
     */
    public Collection<FlowCount> getFlowsInfo(final List<FlowInfo> flows) {
        LOGGER.info("Inside ServiceFlowImpl method getFlowCount");
        Map<FlowCount, FlowCount> infoByFlowInfo = new HashMap<>();

        if (!CollectionUtil.isEmpty(flows)) {
            flows.forEach((flow) -> {
                FlowCount flowInfo = new FlowCount();
                flowInfo.setSrcSwitch(flow.getSourceSwitch());
                flowInfo.setDstSwitch(flow.getTargetSwitch());
                flowInfo.setFlowCount(1);

                if(infoByFlowInfo.containsKey(flowInfo)) {
                    infoByFlowInfo.get(flowInfo).incrementFlowCount();
                } else {
                    infoByFlowInfo.put(flowInfo, flowInfo);
                }
            });
        }
        LOGGER.info("exit ServiceSwitchImpl method getFlowCount");
        return infoByFlowInfo.values();
    }

    /**
     * Gets the path link.
     *
     * @param flowid the flowid
     * @return the path link
     */
    public FlowPath getFlowPath(final String flowid) {

        LOGGER.info("Inside ServiceFlowImpl method getFlowPath ");
        FlowPath pathResponse = new FlowPath();
        PathLinkResponse[] pathLinkResponse = flowsIntegrationService.getFlowPaths();

        if (pathLinkResponse != null) {
            pathResponse = flowDataUtil.getFlowPath(flowid, pathLinkResponse);
        }
        LOGGER.info("exit ServiceFlowImpl method getFlowPath ");
        return pathResponse;
    }

}
