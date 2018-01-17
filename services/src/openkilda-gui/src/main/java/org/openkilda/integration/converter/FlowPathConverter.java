package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;

import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathInfoData;
import org.openkilda.model.PathNode;
import org.openkilda.utility.CollectionUtil;

public final class FlowPathConverter {

    private FlowPathConverter() {
    }

    /**
     * Gets the flow path.
     *
     * @param flowId the flowid
     * @param pathLinkResponse the path link response
     * @return the link path
     */
    public static FlowPath getFlowPath(final String flowId,
            final PathLinkResponse[] pathLinkResponse) {
        FlowPath flowPath = new FlowPath();

        PathInfoData pathInfo = new PathInfoData();
        if(pathLinkResponse != null) {
            for(PathLinkResponse pathResponse : pathLinkResponse) {
                if (pathResponse != null) {
                    if (flowId.equalsIgnoreCase(pathResponse.getFlowid())) {
                        List<PathNode> forwardPath = setForwardPath(pathInfo, pathResponse);
                        setReversePath(pathInfo, forwardPath);
                        break;
                    }
                }
            }
        }
        flowPath.setFlowpath(pathInfo);
        return flowPath;
    }

    /**
     * Sets the reverse path.
     *
     * @param flowPath the flow path
     * @param forwardPath the forward path
     */
    private static void setReversePath(final PathInfoData flowPath, final List<PathNode> forwardPath) {
        List<PathNode> reversePath = new ArrayList<PathNode>();
        if (!CollectionUtil.isEmpty(forwardPath)) {
            int forwardPathSize = forwardPath.size();
            int sequence_id = 0;
            for (int k = forwardPathSize - 1; k >= 0; k--) {
                PathNode pathNodeRes = forwardPath.get(k);
                PathNode pathNode = new PathNode();
                if (pathNodeRes != null) {
                    pathNode.setSwitchId(pathNodeRes.getSwitchId());
                    pathNode.setInPortNo(pathNodeRes.getOutPortNo());
                    pathNode.setOutPortNo(pathNodeRes.getInPortNo());
                }
                pathNode.setSeqId(sequence_id);
                reversePath.add(pathNode);
                sequence_id++;
            }
        }
        flowPath.setReversePath(reversePath);
    }

    /**
     * Sets the forward path.
     *
     * @param flowPath the flow path
     * @param pathResponse the path response
     * @return the list
     */
    private static List<PathNode> setForwardPath(final PathInfoData flowPath, final PathLinkResponse pathResponse) {
        boolean addSwitchInfo;
        List<PathNode> forwardPath = new ArrayList<PathNode>();

        int seq_id = 0;
        PathNode pathNode = new PathNode();
        pathNode.setInPortNo(pathResponse.getSrcPort());
        pathNode.setSwitchId(pathResponse.getSrcSwitch());

        org.openkilda.integration.model.response.PathInfoData pathInfoData = pathResponse.getFlowpath();
        if (pathInfoData != null) {
            List<org.openkilda.integration.model.response.PathNode> pathList = pathInfoData.getPath();
            int pathNodeSize = pathList.size();
            for (int j = 0; j < pathNodeSize; j++) {
                org.openkilda.integration.model.response.PathNode path = pathList.get(j);
                if (path != null) {
                    if (path.getSeqId() % 2 == 0) {
                        pathNode.setOutPortNo(path.getPortNo());
                    } else {
                        pathNode.setInPortNo(path.getPortNo());
                        pathNode.setSwitchId(path.getSwitchId());
                    }
                    addSwitchInfo = checkAddSwitch(seq_id, forwardPath, pathNode);
                    if (addSwitchInfo) {
                        pathNode = new PathNode();
                        seq_id++;
                    }
                }
            }
        }
        pathNode.setOutPortNo(pathResponse.getDstPort());
        addSwitchInfo = checkAddSwitch(seq_id, forwardPath, pathNode);
        if (addSwitchInfo) {
            pathNode = new PathNode();
            seq_id++;
        }
        flowPath.setForwardPath(forwardPath);
        return forwardPath;
    }

    /**
     * Check add switch.
     *
     * @param seq_id the seq_id
     * @param forwardPath the forward path
     * @param node1 the node1
     * @return true, if successful
     */
    private static boolean checkAddSwitch(final int seq_id, final List<PathNode> forwardPath, final PathNode node) {
        boolean isAdd = false;
        if (node.getInPortNo() != null && node.getOutPortNo() != null) {
            node.setSeqId(seq_id);
            forwardPath.add(node);
            isAdd = true;
        }
        return isAdd;
    }
}
