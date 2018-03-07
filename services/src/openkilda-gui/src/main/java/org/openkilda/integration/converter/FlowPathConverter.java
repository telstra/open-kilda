package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;

import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.integration.model.response.PathInfoData;
import org.openkilda.integration.model.response.FlowPathInfoData;
import org.openkilda.integration.model.response.PathNode;
import org.openkilda.model.FlowPath;

public final class FlowPathConverter {

    /**
     * Gets the flow path.
     *
     * @param flowId the flowid
     * @param FlowPayload the Flow Payload
     * @return the flow path
     */
    public static FlowPath getFlowPath(final String flowId, final FlowPayload flowPayload) {
        PathInfoData pathInfo =
                new PathInfoData(setPath(flowPayload.getForward()),
                        setPath(flowPayload.getReverse()));
        return new FlowPath(flowId, pathInfo);
    }

    /**
     * Sets the path.
     *
     * @param FlowPathInfoData the flow path info data
     * @return the {@link PathNode} list
     */
    private static List<PathNode> setPath(final FlowPathInfoData flowPathInfoData) {
        List<PathNode> pathNodes = new ArrayList<PathNode>();
        org.openkilda.integration.model.response.PathInfoData flowpath =
                flowPathInfoData.getFlowpath();
        List<org.openkilda.integration.model.response.PathNode> paths = flowpath.getPath();
        Integer inport = null;
        Integer seq_id = 0;
        if (paths != null && !paths.isEmpty()) {
            for (org.openkilda.integration.model.response.PathNode path : paths) {
                if (path.getSeqId() == 0) {
                    pathNodes.add(new PathNode(seq_id, flowPathInfoData.getSrcPort(), path
                            .getPortNo(), flowPathInfoData.getSrcSwitch()));
                    seq_id++;
                } else {
                    if (path.getSeqId() % 2 == 0) {
                        pathNodes.add(new PathNode(seq_id, inport, path.getPortNo(),
                                path.getSwitchId()));
                        seq_id++;
                    } else
                        inport = path.getPortNo();
                }
            }
        }
        pathNodes.add(new PathNode(seq_id, inport, flowPathInfoData.getDstPort(), flowPathInfoData
                .getDstSwitch()));
        return pathNodes;
    }

}
