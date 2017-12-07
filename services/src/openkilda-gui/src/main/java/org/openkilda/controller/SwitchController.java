package org.openkilda.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchRelationData;
import org.openkilda.switchhelper.ProcessSwitchDetails;
import org.openkilda.utility.MetricsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
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
	private ProcessSwitchDetails processSwitchDetails;

	/**
	 * Gets the switches detial.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the switches detial
	 */
	@RequestMapping(method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getSwitchesDetial(
			ModelMap model, HttpServletRequest request) {
		log.info("inside controller method getSwitchesDetial");
		SwitchRelationData switchDataList = new SwitchRelationData();

		try {
			switchDataList = processSwitchDetails.getswitchdataList();
		} catch (Exception exception) {
			log.error("Exception in getSwitchesDetial "
					+ exception.getMessage());
		}
		model.addAttribute("getSwitchesDetial", switchDataList);
		return new ResponseEntity<Object>(switchDataList, HttpStatus.OK);
	}

	/**
	 * Gets the ports detail switch id.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @param switchId
	 *            the switch id
	 * @return the ports detail switch id
	 */
	@RequestMapping(value = "/{switchId}/ports", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getPortsDetailSwitchId(
			ModelMap model, HttpServletRequest request,
			@PathVariable String switchId) {

		log.info("inside getPortsDetailSwitchId : SwitchId " + switchId);
		List<PortInfo> portResponse = null;
		try {
			portResponse = processSwitchDetails
					.getPortResponseBasedOnSwitchId(switchId);
		} catch (Exception exception) {
			log.error("Exception in getPortsDetailSwitchId : "
					+ exception.getMessage());
		}
		model.addAttribute("getPortsDetailSwitchId", portResponse);
		return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
	}

	/**
	 * Gets the flows detail.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the flows detail
	 */
	@RequestMapping(value = "/flows", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getFlowsDetail(ModelMap model,
			HttpServletRequest request) {

		log.info("inside getFlowsDetail ");
		SwitchRelationData flowResponse = null;
		try {
			flowResponse = processSwitchDetails.getAllFlows();
		} catch (Exception exception) {
			log.error("Exception in getFlowsDetail " + exception.getMessage());
		}
		model.addAttribute("getFlowsDetail", flowResponse);
		return new ResponseEntity<Object>(flowResponse, HttpStatus.OK);
	}

	/**
	 * Gets the links detail.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the links detail
	 */
	@RequestMapping(value = "/links", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getLinksDetail(ModelMap model,
			HttpServletRequest request) {

		log.info("inside getLinksDetail");
		SwitchRelationData portResponse = null;
		try {
			portResponse = processSwitchDetails.getAllLinks();
		} catch (Exception exception) {
			log.error(" Exception in getLinksDetail "
					+ ExceptionUtils.getFullStackTrace(exception));
		}
		model.addAttribute("getLinksDetail", portResponse);
		return new ResponseEntity<Object>(portResponse, HttpStatus.OK);
	}

	/**
	 * Gets the metric detail.
	 *
	 * @param model
	 *            the model
	 * @param request
	 *            the request
	 * @return the metric detail
	 */
	@RequestMapping(value = "/metrics", method = RequestMethod.GET)
	public @ResponseBody ResponseEntity<Object> getMetricDetail(ModelMap model,
			HttpServletRequest request) {

		log.info("inside getMetricDetail");
		List<String> metricsList = null;
		try {
			metricsList = MetricsUtil.getMetricList();
		} catch (Exception exception) {
			log.error("Exception in getMetricDetail "
					+ ExceptionUtils.getFullStackTrace(exception));
		}
		return new ResponseEntity<Object>(metricsList, HttpStatus.OK);
	}
}
