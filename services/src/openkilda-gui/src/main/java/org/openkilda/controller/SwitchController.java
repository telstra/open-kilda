package org.openkilda.controller;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.openkilda.model.response.FlowsCount;
import org.openkilda.model.response.PathResponse;
import org.openkilda.model.response.PortInfo;
import org.openkilda.model.response.SwitchRelationData;
import org.openkilda.service.ServiceSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The Class SwitchController.
 * 
 * @author sumitpal.singh
 * 
 */
@Controller
@RequestMapping(value = "/switch")
public class SwitchController {

	/** The Constant log. */
	private static final Logger log = Logger.getLogger(SwitchController.class);

	/** The process switch details. */
	@Autowired
	private ServiceSwitch serviceSwitch;

	/**
	 * Gets the switches detail.
	 *
	 * @return the switches detail
	 */
	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getSwitchesDetail() {
		log.info("Inside controller method getSwitchesdetail");
		SwitchRelationData switchDataList = new SwitchRelationData();

		try {
			switchDataList = serviceSwitch.getswitchdataList();
		} catch (Exception exception) {
			log.error("Exception in getSwitchesDetail "
					+ exception.getMessage());
		}
		log.info("exit controller method getSwitchesdetail");
		return new ResponseEntity<Object>(switchDataList, HttpStatus.OK);
	}

	/**
	 * Gets the ports detail switch id.
	 *
	 * @param switchId
	 *            the switch id
	 * @return the ports detail switch id
	 */
	@RequestMapping(value = "/{switchId}/ports", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getPortsDetailSwitchId(
			@PathVariable String switchId) {

		log.info("Inside SwitchController method getPortsDetailSwitchId : SwitchId "
				+ switchId);
		List<PortInfo> portResponse = null;
		try {
			portResponse = serviceSwitch
					.getPortResponseBasedOnSwitchId(switchId);
		} catch (Exception exception) {
			log.error("Exception in getPortsDetailSwitchId : "
					+ exception.getMessage());
		}
		log.info("exit SwitchController method getPortsDetailSwitchId ");
		return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
	}

	/**
	 * Gets the links detail.
	 *
	 * @return the links detail
	 */
	@RequestMapping(value = "/links", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getLinksDetail() {

		log.info("Inside SwitchController method getLinksDetail");
		SwitchRelationData portResponse = null;
		try {
			portResponse = serviceSwitch.getAllLinks();
		} catch (Exception exception) {
			log.error(" Exception in getLinksDetail " + exception.getMessage());
		}
		log.info("exit SwitchController method getLinksDetail");
		return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
	}

	/**
	 * Gets the path link.
	 *
	 * @param flowid
	 *            the flowid
	 * @return the path link
	 */
	@RequestMapping(value = "/links/path/{flowid}", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getPathLink(
			@PathVariable String flowid) {

		log.info("Inside SwitchController method getPathLink ");
		PathResponse pathResponse = null;
		try {
			pathResponse = serviceSwitch.getPathLink(flowid);
		} catch (Exception exception) {
			log.error("Exception in getPathLink " + exception.getMessage());
		}
		log.info("exit SwitchController method getPathLink ");
		return new ResponseEntity<Object>(pathResponse, HttpStatus.OK);
	}

	/**
	 * Gets the flow count.
	 *
	 * @return the flow count
	 */
	@RequestMapping(value = "/flowcount", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getFlowCount() {

		log.info("Inside SwitchController method getFlowCount ");
		List<FlowsCount> flowsCount = new ArrayList<FlowsCount>();
		SwitchRelationData flowResponse = null;
		try {
			flowResponse = serviceSwitch.getTopologyFlows();
		} catch (Exception exception) {
			log.error("Exception in getFlowCount " + exception.getMessage());
		}

		if (flowResponse != null)
			flowsCount = serviceSwitch.getFlowCount(flowResponse);
		log.info("exit SwitchController method getFlowCount ");
		return new ResponseEntity<Object>(flowsCount, HttpStatus.OK);
	}

	/**
	 * Gets the topology flows.
	 *
	 * @return the topology flows
	 */
	@RequestMapping(value = "/flows", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getTopologyFlows() {

		log.info("Inside SwitchController method getTopologyFlows");
		SwitchRelationData switchRelationData = serviceSwitch
				.getTopologyFlows();
		log.info("exit SwitchController method getTopologyFlows");
		return new ResponseEntity<Object>(switchRelationData, HttpStatus.OK);
	}
}
