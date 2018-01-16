package org.openkilda.service.helper;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.openkilda.integration.model.response.PathInfoData;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.integration.model.response.PathNode;
import org.openkilda.model.response.FlowPath;
import org.openkilda.utility.CollectionUtil;

/**
 * The Class FlowDataUtil.
 *
 * @author Gaurav Chugh
 */
@Component
public class FlowHelper {

    /** The Constant log. */
    private final Logger LOGGER = Logger.getLogger(FlowHelper.class);

    /**
     * Gets the flow path.
     *
     * @param flowid the flowid
     * @param pathLinkResponse the path link response
     * @return the link path
     */
    public FlowPath getFlowPath(final String flowid,
            final PathLinkResponse[] pathLinkResponse) {

        LOGGER.info("Inside getLinkPath .");
        FlowPath returnPathResponse = new FlowPath();

        PathInfoData flowPath = new PathInfoData();
        if(pathLinkResponse != null) {
            for(PathLinkResponse pathResponse : pathLinkResponse) {
                if (pathResponse != null) {
                    if (flowid.equalsIgnoreCase(pathResponse.getFlowid())) {
                        List<PathNode> forwardPath = setForwardPath(flowPath, pathResponse);
                        setReversePath(flowPath, forwardPath);
                        break;
                    }
                }
            }
        }
        returnPathResponse.setFlowpath(flowPath);
        LOGGER.info("exit getLinkPath .");
        return returnPathResponse;
    }

    /**
     * Sets the reverse path.
     *
     * @param flowPath the flow path
     * @param forwardPath the forward path
     */
    private void setReversePath(final PathInfoData flowPath, final List<PathNode> forwardPath) {

        LOGGER.info("Inside setReversePath");
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
        LOGGER.info("exit setReversePath");
        flowPath.setReversePath(reversePath);
    }

    /**
     * Sets the forward path.
     *
     * @param flowPath the flow path
     * @param pathResponse the path response
     * @return the list
     */
    private List<PathNode> setForwardPath(final PathInfoData flowPath, final PathLinkResponse pathResponse) {
        LOGGER.info("Inside setForwardPath");
        boolean addSwitchInfo;
        List<PathNode> forwardPath = new ArrayList<PathNode>();

        int seq_id = 0;
        PathNode pathNode = new PathNode();
        pathNode.setInPortNo(pathResponse.getSrcPort());
        pathNode.setSwitchId(pathResponse.getSrcSwitch());

        PathInfoData pathInfoData = pathResponse.getFlowpath();
        if (pathInfoData != null) {
            List<PathNode> pathList = pathInfoData.getPath();
            int pathNodeSize = pathList.size();

            if (pathList != null && !pathList.isEmpty()) {
                for (int j = 0; j < pathNodeSize; j++) {
                    PathNode path = pathList.get(j);
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
        }
        pathNode.setOutPortNo(pathResponse.getDstPort());
        addSwitchInfo = checkAddSwitch(seq_id, forwardPath, pathNode);
        if (addSwitchInfo) {
            pathNode = new PathNode();
            seq_id++;
        }
        flowPath.setForwardPath(forwardPath);
        LOGGER.info("exit setForwardPath");
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
    private boolean checkAddSwitch(final int seq_id, final List<PathNode> forwardPath, final PathNode node) {

        boolean isAdd = false;
        if (node.getInPortNo() != null && node.getOutPortNo() != null) {
            node.setSeqId(seq_id);
            forwardPath.add(node);
            isAdd = true;
        }
        return isAdd;
    }
}
