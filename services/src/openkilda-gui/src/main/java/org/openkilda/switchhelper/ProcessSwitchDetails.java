package org.openkilda.switchhelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.openkilda.helper.RestClientManager;
import org.openkilda.model.Destination;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Path;
import org.openkilda.model.PortDesc;
import org.openkilda.model.PortInfo;
import org.openkilda.model.PortModel;
import org.openkilda.model.PortSwitch;
import org.openkilda.model.Source;
import org.openkilda.model.SwitchInfo;
import org.openkilda.model.SwitchRelationData;
import org.openkilda.model.Switchrelation;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtils;
import org.openkilda.utility.SwitchDataUtil;
import org.openkilda.utility.Util;
import org.openkilda.ws.response.FlowResponse;
import org.openkilda.ws.response.FlowStatusResponse;
import org.openkilda.ws.response.LinkResponse;
import org.openkilda.ws.response.PortDetailResponse;
import org.openkilda.ws.response.PortResponse;
import org.openkilda.ws.response.SwitchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class ProcessSwitchDetails.
 * 
 * @author Gaurav Chugh
 */
@Service
public class ProcessSwitchDetails {

    /** The util. */
    @Autowired
    private Util util;

    @Autowired
    private SwitchDataUtil switchDataUtil;

    /** The rest client manager. */
    @Autowired
    private RestClientManager restClientManager;

    /** The application properties. */
    @Autowired
    ApplicationProperties applicationProperties;

    /** The Constant log. */
    private static final Logger log = Logger.getLogger(ProcessSwitchDetails.class);

    /**
     * get All SwitchList.
     * 
     * @return SwitchRelationData
     */
    public SwitchRelationData getswitchdataList() {

        log.info("Inside getswitchdataList ");
        SwitchRelationData switchRelationData = new SwitchRelationData();
        List<SwitchInfo> switchInfoList = new ArrayList<SwitchInfo>();
        List<SwitchResponse> switchResponseList = null;

        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getSwitches(), HttpMethod.GET,
                            "", "", "");
            if (RestClientManager.isValidResponse(response)) {

                switchResponseList =
                        restClientManager.getResponseList(response, SwitchResponse.class);

            }
        } catch (Exception exception) {
            log.error("Exception in getswitchdataList " + exception.getMessage());
        }
        log.info("getswitchdataList GET_SWITCH_DATA_URL api call response is " + switchResponseList);

        if (switchResponseList != null) {
            for (SwitchResponse switchResponse : switchResponseList) {
                SwitchInfo switchInfo = new SwitchInfo();
                String inetAddress = switchResponse.getInetAddress();
                String[] splitInetAddress = inetAddress.split("/");
                switchInfo.setController(splitInetAddress[0]);
                switchInfo.setAddress(splitInetAddress[1]);
                switchInfo.setDescription(switchResponse.getOpenFlowVersion());
                switchInfo.setName(switchResponse.getSwitchDPID());

                switchInfoList.add(switchInfo);
            }
        }
        switchRelationData.setSwitches(switchInfoList);
        log.info("exit getswitchdataList ");
        return switchRelationData;
    }

    /**
     * get All Links.
     *
     * @param switchRelationData the switch relation data
     * @return SwitchRelationData
     */
    public SwitchRelationData getAllLinks() {

        log.info("Inside getAllLinks ");
        SwitchRelationData switchRelationData = new SwitchRelationData();
        List<Switchrelation> switchrelationList = new ArrayList<Switchrelation>();
        List<LinkResponse> linkResponseList = null;

        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getLinks(), HttpMethod.GET, "",
                            "", "");
            if (RestClientManager.isValidResponse(response)) {

                linkResponseList = restClientManager.getResponseList(response, LinkResponse.class);
            }
        } catch (Exception exception) {
            log.error("Exception in getAllLinks " + exception.getMessage());
        }
        log.info("getAllLinks GET_LINK_DATA_URL api call response is " + linkResponseList);

        if (linkResponseList != null) {

            for (LinkResponse linkResponse : linkResponseList) {
                Switchrelation switchrelation = new Switchrelation();
                Integer availableBanbwidth = linkResponse.getAvailableBandwidth();
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

                List<Path> pathList = linkResponse.getPath();
                if (pathList != null) {
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
                        Switchrelation switchrelationObj = switchrelationList.get(i);
                        if (switchrelationObj.getDstPort() == srcPort
                                && switchrelationObj.getDstSwitch().equalsIgnoreCase(srcSwitchId)
                                && switchrelationObj.getSrcPort() == dstPort
                                && switchrelationObj.getSrcSwitch().equalsIgnoreCase(dstSwitchId)) {
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
        log.info("exit getAllLinks ");
        return switchRelationData;
    }

    /**
     * get All Flows.
     *
     * @return SwitchRelationData
     */
    public SwitchRelationData getAllFlows() {

        log.info("Inside getAllFlows ");
        SwitchRelationData switchRelationData = new SwitchRelationData();
        List<FlowPath> flowPathList = new ArrayList<FlowPath>();
        List<FlowResponse> flowResponseList = new ArrayList<FlowResponse>();
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getFlows(), HttpMethod.GET, "",
                            "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {

                flowPathList = restClientManager.getResponseList(response, FlowPath.class);
            }

        } catch (Exception exception) {
            log.error("Exception in getAllFlows " + exception.getMessage());
        }
        if (flowPathList != null) {
            for (FlowPath flowPath : flowPathList) {
                FlowResponse flowRsponse = new FlowResponse();
                flowRsponse.setFlowid(flowPath.getFlowid());
                flowRsponse.setMaximumBandwidth(flowPath.getMaximumBandwidth());
                flowRsponse.setDescription(flowPath.getDescription());

                Source source = flowPath.getSource();
                flowRsponse.setSourceSwitch(source.getSwitchId());
                flowRsponse.setSrcPort(source.getPortId());
                flowRsponse.setSrcVlan(source.getVlanId());

                Destination destination = flowPath.getDestination();
                flowRsponse.setTargetSwitch(destination.getSwitchId());
                flowRsponse.setDstPort(destination.getPortId());
                flowRsponse.setDstVlan(destination.getVlanId());
                FlowStatusResponse flowStatusResponse = null;
                String status = "";
                try {
                    HttpResponse response =
                            restClientManager.invoke(applicationProperties.getFlowStatus()
                                    + flowPath.getFlowid(), HttpMethod.GET, "", "",
                                    util.kildaAuthHeader());
                    if (RestClientManager.isValidResponse(response)) {

                        flowStatusResponse =
                                restClientManager.getResponse(response, FlowStatusResponse.class);

                        status = flowStatusResponse.getStatus();
                    }
                } catch (Exception exception) {
                    log.error("Exception in getAllFlows " + exception.getMessage());
                }
                flowRsponse.setStatus(status);
                flowResponseList.add(flowRsponse);
            }
        }

        switchRelationData.setFlowResponse(flowResponseList);
        log.info("exit getAllFlows ");
        return switchRelationData;
    }

    /**
     * get All Ports.
     * 
     * @return List<PortInfo>
     */
    public List<PortInfo> getPortResponse() {

        log.info("Inside getPortResponse");
        PortResponse portResponse = null;
        List<PortInfo> switchPortsInfoList = new ArrayList<PortInfo>();
        List<PortModel> portModelList = new ArrayList<PortModel>();
        ObjectMapper mapper = new ObjectMapper();

        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getPorts(), HttpMethod.GET, "",
                            "", "");
            if (RestClientManager.isValidResponse(response)) {

                portResponse = restClientManager.getResponse(response, PortResponse.class);
            }

        } catch (Exception exception) {
            log.error("Exception in getPortResponse " + exception.getMessage());
        }
        log.info("getPortResponse GET_PORT_DATA_URL api call response is " + portResponse);

        if (portResponse != null) {
            List<PortSwitch> switchList = portResponse.getSwitches();
            portModelList = switchDataUtil.getPortModelList(switchList);

            try {
                JSONObject jsonObject = null;
                HttpResponse response =
                        restClientManager.invoke(applicationProperties.getSwitchPorts(),
                                HttpMethod.GET, "", "", "");
                if (RestClientManager.isValidResponse(response)) {
                    String responseEntity = IoUtils.getData(response.getEntity().getContent());
                    jsonObject = mapper.readValue(responseEntity, JSONObject.class);
                }

                log.info("getPortResponse GET_SWITCH_PORT_DATA_URL api call response is "
                        + jsonObject);
                mapper = new ObjectMapper();
                Set<?> keySet = jsonObject.keySet();
                Iterator<?> itr = keySet.iterator();

                while (itr.hasNext()) {
                    String key = (String) itr.next();
                    Object object = jsonObject.get(key);
                    String val = JSONValue.toJSONString(object);
                    PortDetailResponse portDetailResponse =
                            mapper.readValue(val, PortDetailResponse.class);
                    List<PortDesc> portDescList = portDetailResponse.getPortDesc();
                    if (portDescList != null) {
                        switchPortsInfoList =
                                switchDataUtil.getSwitchPortInfo(portDescList, key, portModelList,
                                        switchPortsInfoList);
                    }
                }
            } catch (Exception exception) {
                log.error("Exception in getPortResponse " + exception.getMessage());
            }
        }

        log.info("exit getPortResponse");
        return switchPortsInfoList;
    }

    /**
     * get ports List based on switch id.
     *
     * @param switchId the switch id
     * @return List<PortInfo>
     */
    public List<PortInfo> getPortResponseBasedOnSwitchId(String switchId) {

        log.info("Inside getPortResponseBasedOnSwitchId");
        List<PortInfo> response = new ArrayList<PortInfo>();
        List<PortInfo> portdetailList = getPortResponse();

        log.info("Inside getPortResponseBasedOnSwitchId, portdetailList" + portdetailList);
        if (portdetailList != null) {
            for (PortInfo PortInfo : portdetailList) {
                if (switchId.equalsIgnoreCase(PortInfo.getSwitchName())) {
                    PortInfo portInfoDetail = new PortInfo();
                    portInfoDetail.setInterfacetype(PortInfo.getInterfacetype());
                    portInfoDetail.setPortName(PortInfo.getPortName());
                    portInfoDetail.setPortNumber(PortInfo.getPortNumber());
                    portInfoDetail.setStatus(PortInfo.getStatus());
                    response.add(portInfoDetail);
                }
            }
        }
        log.info("exit getPortResponseBasedOnSwitchId");
        return response;
    }
}
