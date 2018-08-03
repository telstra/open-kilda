package org.openkilda.integration.converter;

import java.util.List;
import java.util.Map;

import org.openkilda.integration.model.response.FlowPathNode;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowPathConverter {

    @Autowired
    SwitchIntegrationService switchIntegrationService;

    /**
     * Gets the flow path.
     *
     * @param flowId the flowid
     * @param FlowPayload the Flow Payload
     * @return the flow path
     */
    public FlowPayload getFlowPath(final String flowId, final FlowPayload flowPayload) {
        Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();
        setSwitchName(flowPayload.getForward(), csNames);
        setSwitchName(flowPayload.getReverse(), csNames);
        return flowPayload;
    }
    
    /**
     * Sets the switch name.
     *
     * @param pathNodes the path nodes
     * @param csNames the cs names
     */
    private void setSwitchName(List<FlowPathNode> pathNodes, Map<String, String> csNames) {
        pathNodes.parallelStream().forEach((pathNode) -> {
            pathNode.setSwitchName(
                    switchIntegrationService.customSwitchName(csNames, pathNode.getSwitchId()));
        });
    }

}
