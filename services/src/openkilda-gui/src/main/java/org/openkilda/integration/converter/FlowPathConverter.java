package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
        Map<String, String> csNames = switchIntegrationService.getCustomSwitchNameFromFile();
        PathInfoData pathInfo = new PathInfoData(setPath(flowPayload.getForward(), csNames),
                setPath(flowPayload.getReverse(), csNames));
        return new FlowPath(flowId, pathInfo);
    }

    /**
     * Sets the path.
     *
     * @param FlowPathInfoData the flow path info data
     * @return the {@link PathNode} list
     */
    private List<PathNode> setPath(FlowPathInfoData flowPathInfoData, Map<String, String> csNames) {
        List<PathNode> pathNodes = new ArrayList<PathNode>();
        PathInfoData flowpath = flowPathInfoData.getFlowpath();
        List<PathNode> paths = flowpath.getPath();
        Set<PathNode> sortedPathSet = new TreeSet<>(paths);
        Integer inport = null;
        String switchId = "";
        Integer seq_id = 0;

        if (paths != null && !paths.isEmpty()) {
            for (PathNode path : sortedPathSet) {
                if (seq_id == 0) {

                    String switchName = switchIntegrationService.customSwitchName(csNames,
                            flowPathInfoData.getSrcSwitch());
                    pathNodes.add(new PathNode(seq_id, flowPathInfoData.getSrcPort(),
                            path.getPortNo(), flowPathInfoData.getSrcSwitch(), switchName));
                    seq_id++;
                } else {
                    if (path.getSwitchId().equalsIgnoreCase(switchId)) {
                        String switchName = switchIntegrationService.customSwitchName(csNames,
                                path.getSwitchId());
                        pathNodes.add(new PathNode(seq_id, inport, path.getPortNo(),
                                path.getSwitchId(), switchName));
                        seq_id++;
                    } else {
                        switchId = path.getSwitchId();
                        inport = path.getPortNo();
                    }
                }
            }
            String switchName = switchIntegrationService.customSwitchName(csNames,
                    flowPathInfoData.getDstSwitch());
            pathNodes.add(new PathNode(seq_id, inport, flowPathInfoData.getDstPort(),
                    flowPathInfoData.getDstSwitch(), switchName));
        } else {
            String switchName = switchIntegrationService.customSwitchName(csNames,
                    flowPathInfoData.getSrcSwitch());
            pathNodes.add(new PathNode(seq_id, flowPathInfoData.getSrcPort(),
                    flowPathInfoData.getDstPort(), flowPathInfoData.getSrcSwitch(), switchName));
        }

        return pathNodes;
    }
}
