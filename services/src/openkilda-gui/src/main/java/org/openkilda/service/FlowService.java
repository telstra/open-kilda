package org.openkilda.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.model.FlowCount;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.utility.CollectionUtil;

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


    /**
     * get All Flows.
     *
     * @return SwitchRelationData
     * @throws Exception
     */
    public List<FlowInfo> getAllFlows() throws IntegrationException {
        return flowsIntegrationService.getFlows();
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
     * @throws IntegrationException
     */
    public FlowPath getFlowPath(final String flowid) throws IntegrationException {
        return flowsIntegrationService.getFlowPath(flowid);
    }
}
