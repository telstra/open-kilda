package org.openkilda.utility;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.openkilda.integration.model.response.PathInfoData;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.integration.model.response.PathNode;
import org.openkilda.model.response.PathResponse;
import org.springframework.stereotype.Component;

/**
 * The Class FlowDataUtil.
 * 
 * @author Gaurav Chugh
 */
@Component
public class FlowDataUtil {

	/** The Constant log. */
	private final Logger log = Logger.getLogger(FlowDataUtil.class);

	/**
	 * Gets the flow path.
	 *
	 * @param flowid
	 *            the flowid
	 * @param pathLinkResponse
	 *            the path link response
	 * @return the link path
	 */
	public PathResponse getFlowPath(String flowid,
			PathLinkResponse[] pathLinkResponse) {

		log.info("Inside getLinkPath .");
		PathResponse returnPathResponse = new PathResponse();
		int pathLinkResponseSize = pathLinkResponse.length;

		PathInfoData flowPath = new PathInfoData();
		if (pathLinkResponseSize > 0) {

			for (int i = 0; i < pathLinkResponseSize; i++) {
				PathLinkResponse pathResponse = pathLinkResponse[i];

				if (pathResponse != null) {
					if (flowid.equalsIgnoreCase(pathResponse.getFlowid())) {
						List<PathNode> forwardPath = setForwardPath(flowPath,
								pathResponse);
						setReversePath(flowPath, forwardPath);
						break;
					}
				}
			}
		}
		returnPathResponse.setFlowpath(flowPath);
		log.info("exit getLinkPath .");
		return returnPathResponse;
	}

	/**
	 * Sets the reverse path.
	 *
	 * @param flowPath
	 *            the flow path
	 * @param forwardPath
	 *            the forward path
	 */
	private void setReversePath(PathInfoData flowPath,
			List<PathNode> forwardPath) {

		log.info("Inside setReversePath");
		List<PathNode> reversePath = new ArrayList<PathNode>();
		if (forwardPath != null && !forwardPath.isEmpty()) {
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
		log.info("exit setReversePath");
		flowPath.setReversePath(reversePath);
	}

	/**
	 * Sets the forward path.
	 *
	 * @param flowPath
	 *            the flow path
	 * @param pathResponse
	 *            the path response
	 * @return the list
	 */
	private List<PathNode> setForwardPath(PathInfoData flowPath,
			PathLinkResponse pathResponse) {

		log.info("Inside setForwardPath");
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
						addSwitchInfo = checkAddSwitch(seq_id, forwardPath,
								pathNode);
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
		log.info("exit setForwardPath");
		return forwardPath;
	}

	/**
	 * Check add switch.
	 *
	 * @param seq_id
	 *            the seq_id
	 * @param forwardPath
	 *            the forward path
	 * @param node1
	 *            the node1
	 * @return true, if successful
	 */
	private boolean checkAddSwitch(int seq_id, List<PathNode> forwardPath,
			PathNode node) {

		boolean isAdd = false;
		if (node.getInPortNo() != null && node.getOutPortNo() != null) {
			node.setSeqId(seq_id);
			forwardPath.add(node);
			isAdd = true;
		}
		return isAdd;
	}
}
