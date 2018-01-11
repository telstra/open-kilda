package org.openkilda.service;

import java.util.List;

import org.openkilda.model.response.FlowsCount;
import org.openkilda.model.response.PathResponse;
import org.openkilda.model.response.PortInfo;
import org.openkilda.model.response.SwitchRelationData;

/**
 * The Interface ServiceSwitch.
 * 
 * @author Gaurav Chugh
 */
public interface ServiceSwitch {

    /**
     * Gets the switchdata list.
     *
     * @return the switchdata list
     */
    SwitchRelationData getswitchdataList();

    /**
     * Gets the port response based on switch id.
     *
     * @param switchId the switch id
     * @return the port response based on switch id
     */
    List<PortInfo> getPortResponseBasedOnSwitchId(String switchId);

    /**
     * Gets the all links.
     *
     * @return the all links
     */
    SwitchRelationData getAllLinks();

    /**
     * Gets the path link.
     *
     * @param flowid the flowid
     * @return the path link
     */
    PathResponse getPathLink(String flowid);

    /**
     * Gets the flow count.
     *
     * @param flowResponse the flow response
     * @return the flow count
     */
    List<FlowsCount> getFlowCount(SwitchRelationData flowResponse);


    /**
     * Gets the topology flows.
     *
     * @return the topology flows
     */
    SwitchRelationData getTopologyFlows();

}
