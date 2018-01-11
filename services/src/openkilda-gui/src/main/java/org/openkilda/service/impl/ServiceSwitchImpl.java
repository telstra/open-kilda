package org.openkilda.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.openkilda.integration.model.response.FlowResponse;
import org.openkilda.integration.model.response.FlowStatusResponse;
import org.openkilda.integration.model.response.LinkResponse;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.integration.model.response.PathNode;
import org.openkilda.integration.model.response.PortDesc;
import org.openkilda.integration.model.response.PortDetailResponse;
import org.openkilda.integration.model.response.SwitchInfo;
import org.openkilda.integration.model.response.SwitchResponse;
import org.openkilda.integration.model.response.Switchrelation;
import org.openkilda.integration.model.response.TopologyFlowsResponse;
import org.openkilda.integration.service.FlowsIntegrationService;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.response.FlowsCount;
import org.openkilda.model.response.PathResponse;
import org.openkilda.model.response.PortInfo;
import org.openkilda.model.response.SwitchRelationData;
import org.openkilda.service.ServiceSwitch;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.FlowDataUtil;
import org.openkilda.utility.SwitchDataUtil;
import org.openkilda.utility.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class ServiceSwitchImpl.
 * 
 * @author Gaurav Chugh
 */
@Service
public class ServiceSwitchImpl implements ServiceSwitch {

	/** The Constant log. */
	private static final Logger log = Logger.getLogger(ServiceSwitchImpl.class);

	/** The util. */
	@Autowired
	private Util util;

	/** The switch data util. */
	@Autowired
	private SwitchDataUtil switchDataUtil;

	/** The flow data util. */
	@Autowired
	private FlowDataUtil flowDataUtil;

	/** The application properties. */
	@Autowired
	private ApplicationProperties applicationProperties;

	/** The flows integration service. */
	@Autowired
	private FlowsIntegrationService flowsIntegrationService;

	/** The switch integration service. */
	@Autowired
	private SwitchIntegrationService switchIntegrationService;

	/**
	 * get All SwitchList.
	 * 
	 * @return SwitchRelationData
	 */
	public SwitchRelationData getswitchdataList() {

		log.info("Inside ServiceSwitchImpl method getswitchdataList ");
		SwitchRelationData switchRelationData = new SwitchRelationData();
		List<SwitchInfo> switchInfoList = new ArrayList<SwitchInfo>();

		List<SwitchResponse> switchResponseList = switchIntegrationService
				.getSwitches();

		log.info("getswitchdataList GET_SWITCH_DATA_URL api call response is "
				+ switchResponseList);

		if (switchResponseList != null && !switchResponseList.isEmpty()) {
			for (SwitchResponse switchResponse : switchResponseList) {
				SwitchInfo switchInfo = new SwitchInfo();
				String inetAddress = switchResponse.getInetAddress();
				if (inetAddress != null) {
					String[] splitInetAddress = inetAddress.split("/");
					switchInfo.setController(splitInetAddress[0]);
					switchInfo.setAddress(splitInetAddress[1]);
				}
				switchInfo.setDescription(switchResponse.getOpenFlowVersion());
				switchInfo.setName(switchResponse.getSwitchDPID());
				switchInfoList.add(switchInfo);
			}
		}
		switchRelationData.setSwitches(switchInfoList);
		log.info("exit ServiceSwitchImpl method getswitchdataList ");
		return switchRelationData;
	}

	/**
	 * get All Links.
	 *
	 * @return SwitchRelationData
	 */
	public SwitchRelationData getAllLinks() {

		log.info("Inside ServiceSwitchImpl method getAllLinks ");
		SwitchRelationData switchRelationData = new SwitchRelationData();
		List<Switchrelation> switchrelationList = new ArrayList<Switchrelation>();

		List<LinkResponse> linkResponseList = switchIntegrationService
				.getIslLinks();

		log.info("getAllLinks GET_LINK_DATA_URL api call response is "
				+ linkResponseList);

		if (linkResponseList != null && !linkResponseList.isEmpty()) {

			for (LinkResponse linkResponse : linkResponseList) {
				Switchrelation switchrelation = new Switchrelation();
				Integer availableBanbwidth = linkResponse
						.getAvailableBandwidth();
				Integer latency = linkResponse.getLatencyNs();
				Integer speed = linkResponse.getSpeed();
				String state = linkResponse.getState();
				Integer dstPort = 0;
				String dstSwitchId = "";
				Integer srcPort = 0;
				String srcSwitchId = "";

				switchrelation.setAvailableBandwidth(availableBanbwidth);
				switchrelation.setLatency(latency);
				switchrelation.setSpeed(speed);
				switchrelation.setState(state);

				List<PathNode> pathList = linkResponse.getPath();
				if (pathList != null && !pathList.isEmpty()) {
					if (pathList.get(0) != null) {
						srcPort = pathList.get(0).getPortNo();
						srcSwitchId = pathList.get(0).getSwitchId();
						switchrelation.setSrcPort(srcPort);
						switchrelation.setSrcSwitch(srcSwitchId);
					}
					if (pathList.get(1) != null) {
						dstPort = pathList.get(1).getPortNo();
						dstSwitchId = pathList.get(1).getSwitchId();
						switchrelation.setDstPort(dstPort);
						switchrelation.setDstSwitch(dstSwitchId);
					}
				}
				boolean isSwitchRelationAdd = false;
				if (switchrelationList.size() == 0) {
					switchrelationList.add(switchrelation);
				} else {
					for (int i = 0; i < switchrelationList.size(); i++) {
						Switchrelation switchrelationObj = switchrelationList
								.get(i);
						if (switchrelationObj.getDstPort() == srcPort
								&& switchrelationObj.getDstSwitch()
										.equalsIgnoreCase(srcSwitchId)
								&& switchrelationObj.getSrcPort() == dstPort
								&& switchrelationObj.getSrcSwitch()
										.equalsIgnoreCase(dstSwitchId)) {
							isSwitchRelationAdd = false;
							break;
						} else {
							isSwitchRelationAdd = true;

						}
					}
					if (isSwitchRelationAdd)
						switchrelationList.add(switchrelation);
				}

			}

		}
		switchRelationData.setSwitchrelation(switchrelationList);
		log.info("exit ServiceSwitchImpl method getAllLinks ");
		return switchRelationData;
	}

	/**
	 * Gets the topology flows.
	 *
	 * @return the topology flows
	 */
	public SwitchRelationData getTopologyFlows() {

		log.info("Inside ServiceSwitchImpl method getTopologyFlows ");
		SwitchRelationData switchRelationData = new SwitchRelationData();

		List<FlowResponse> flowResponseList = new ArrayList<FlowResponse>();

		List<TopologyFlowsResponse> flowPathList = flowsIntegrationService
				.getTopologyFlows();

		if (flowPathList != null && !flowPathList.isEmpty()) {
			for (TopologyFlowsResponse flowPath : flowPathList) {
				setFlowResponse(flowResponseList, flowPath);
			}
		}
		switchRelationData.setTopologyFlowResponse(flowResponseList);

		log.info("exit ServiceSwitchImpl method getTopologyFlows ");
		return switchRelationData;
	}

	/**
	 * Sets the flow response.
	 *
	 * @param flowResponseList
	 *            the flow response list
	 * @param flowPath
	 *            the flow path
	 */
	private void setFlowResponse(List<FlowResponse> flowResponseList,
			TopologyFlowsResponse flowPath) {

		log.info("Inside ServiceSwitchImpl method setFlowResponse");
		FlowResponse flowRsponse = new FlowResponse();
		flowRsponse.setFlowid(flowPath.getFlowid());
		flowRsponse.setMaximumBandwidth(flowPath.getBandwidth());
		flowRsponse.setDescription(flowPath.getDescription());
		flowRsponse.setSourceSwitch(flowPath.getSrcSwitch());
		flowRsponse.setSrcPort(flowPath.getSrcPort());
		flowRsponse.setSrcVlan(flowPath.getSrcVlan());
		flowRsponse.setTargetSwitch(flowPath.getDstSwitch());
		flowRsponse.setDstPort(flowPath.getDstPort());
		flowRsponse.setDstVlan(flowPath.getDstVlan());

		String status = "";
		FlowStatusResponse flowStatusResponse = flowsIntegrationService
				.getFlowStatus(flowPath.getFlowid());

		if (flowStatusResponse != null)
			status = flowStatusResponse.getStatus();
		flowRsponse.setStatus(status);
		boolean isFlowIdAvailable = false;

		for (FlowResponse flowResponseRes : flowResponseList) {
			if (flowResponseRes.getFlowid().equalsIgnoreCase(
					flowRsponse.getFlowid())) {
				isFlowIdAvailable = true;
				break;
			} else
				isFlowIdAvailable = false;

		}
		if (!isFlowIdAvailable)
			flowResponseList.add(flowRsponse);
		log.info("exit ServiceSwitchImpl method setFlowResponse");
	}

	/**
	 * get All Ports.
	 *
	 * @param switchId
	 *            the switch id
	 * @return List<PortInfo>
	 */
	public List<PortInfo> getPortResponseBasedOnSwitchId(String switchId) {

		log.info("Inside ServiceSwitchImpl method getPortResponseBasedOnSwitchId");
		List<PortInfo> switchPortsInfoList = new ArrayList<PortInfo>();
		ObjectMapper mapper = new ObjectMapper();
		String key = switchId;
		try {
			JSONObject jsonObject = switchIntegrationService.getSwitchPorts();
			log.info("getPortResponseBasedOnSwitchId GET_SWITCH_PORT_DATA_URL api call response is "
					+ jsonObject);

			if (jsonObject != null) {
				Object object = jsonObject.get(key);
				if (object != null) {
					String val = JSONValue.toJSONString(object);
					PortDetailResponse portDetailResponse = mapper.readValue(
							val, PortDetailResponse.class);

					List<PortDesc> portDescList = portDetailResponse
							.getPortDesc();
					if (portDescList != null && !portDescList.isEmpty()) {
						switchPortsInfoList = switchDataUtil.getSwitchPortInfo(
								portDescList, key, switchPortsInfoList);
					}
				}
			}
		} catch (Exception exception) {
			log.error("Exception in getPortResponseBasedOnSwitchId "
					+ exception.getMessage());
		}

		log.info("exit ServiceSwitchImpl method getPortResponseBasedOnSwitchId");
		return switchPortsInfoList;
	}

	/**
	 * Gets the path link.
	 *
	 * @param flowid
	 *            the flowid
	 * @return the path link
	 */
	public PathResponse getPathLink(String flowid) {

		log.info("Inside ServiceSwitchImpl method getPathLink ");
		PathResponse pathResponse = new PathResponse();
		PathLinkResponse[] pathLinkResponse = flowsIntegrationService
				.getFlowPaths();

		if (pathLinkResponse != null)
			pathResponse = flowDataUtil.getFlowPath(flowid, pathLinkResponse);
		log.info("exit ServiceSwitchImpl method getPathLink ");
		return pathResponse;
	}

	/**
	 * Gets the flow count.
	 *
	 * @param switchRelationData
	 *            the switch relation data
	 * @return the flow count
	 */
	public List<FlowsCount> getFlowCount(SwitchRelationData switchRelationData) {

		log.info("Inside ServiceSwitchImpl method getFlowCount");
		List<FlowsCount> flowCountList = new ArrayList<FlowsCount>();
		HashMap<String, Integer> map = new HashMap<String, Integer>();

		List<FlowResponse> flowResponseList = switchRelationData
				.getTopologyFlowResponse();

		if (flowResponseList != null && !flowResponseList.isEmpty()) {
			for (FlowResponse flowResponse : flowResponseList) {
				String src_switch = flowResponse.getSourceSwitch();
				String dst_switch = flowResponse.getTargetSwitch();
				String key_switch = src_switch + "-" + dst_switch;
				if (map.containsKey(key_switch))
					map.put(key_switch, map.get(key_switch) + 1);
				else
					map.put(key_switch, 1);
			}
		}
		if (map != null && !map.isEmpty()) {
			for (Map.Entry<String, Integer> entry : map.entrySet()) {
				String key = entry.getKey();
				Integer value = entry.getValue();
				String[] switches = key.split("-");
				if (switches.length > 0) {
					FlowsCount flowCount = new FlowsCount();
					flowCount.setSrcSwitch(switches[0]);
					flowCount.setDstSwitch(switches[1]);
					flowCount.setFlowCount(value);
					flowCountList.add(flowCount);
				}

			}
		}
		log.info("exit ServiceSwitchImpl method getFlowCount");
		return flowCountList;
	}

}
