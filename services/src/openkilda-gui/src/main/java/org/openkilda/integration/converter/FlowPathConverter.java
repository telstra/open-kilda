package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openkilda.integration.model.response.FlowPathInfoData;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.integration.model.response.PathInfoData;
import org.openkilda.integration.model.response.PathNode;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.FlowPath;
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
    public FlowPath getFlowPath(final String flowId, final FlowPayload flowPayload) {
        PathInfoData pathInfo = new PathInfoData(setPath(flowPayload.getForward()),
                setPath(flowPayload.getReverse()));
        return new FlowPath(flowId, pathInfo);
    }

    /**
     * Sets the path.
     *
     * @param FlowPathInfoData the flow path info data
     * @return the {@link PathNode} list
     */
    private List<PathNode> setPath(FlowPathInfoData flowPathInfoData) {
        List<PathNode> pathNodes = new ArrayList<PathNode>();
        org.openkilda.integration.model.response.PathInfoData flowpath =
                flowPathInfoData.getFlowpath();
        List<org.openkilda.integration.model.response.PathNode> paths = flowpath.getPath();
        Integer inport = null;
        Integer seq_id = 0;
        Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();

        if (paths != null && !paths.isEmpty()) {

            for (org.openkilda.integration.model.response.PathNode path : paths) {
                if (path.getSeqId() == 1) {

                    String switchName = switchIntegrationService.customSwitchName(csNames,
                            flowPathInfoData.getSrcSwitch());
                    pathNodes.add(new PathNode(seq_id, flowPathInfoData.getSrcPort(),
                            path.getPortNo(), switchName));
                    seq_id++;
                } else {
                    if (path.getSeqId() % 2 == 1) {
                        String switchName = switchIntegrationService.customSwitchName(csNames,
                                path.getSwitchId());
                        pathNodes.add(new PathNode(seq_id, inport, path.getPortNo(), switchName));
                        seq_id++;
                    } else
                        inport = path.getPortNo();
                }
            }
        }
        String switchName =
                switchIntegrationService.customSwitchName(csNames, flowPathInfoData.getDstSwitch());
        pathNodes.add(new PathNode(seq_id, inport, flowPathInfoData.getDstPort(), switchName));
        return pathNodes;
    }
}
