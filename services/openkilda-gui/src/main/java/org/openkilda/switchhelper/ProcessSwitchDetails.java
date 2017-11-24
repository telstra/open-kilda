package org.openkilda.switchhelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.openkilda.helper.APIHelper;
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
import org.openkilda.utility.IConstants;
import org.openkilda.utility.SwitchDataUtil;
import org.openkilda.ws.response.FlowResponse;
import org.openkilda.ws.response.LinkResponse;
import org.openkilda.ws.response.PortDetailResponse;
import org.openkilda.ws.response.PortResponse;
import org.openkilda.ws.response.SwitchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class ProcessSwitchDetails.
 * 
 * @author Gaurav Chugh
 */
@Service
public class ProcessSwitchDetails {

    /** The api helper. */
    @Autowired
    private APIHelper apiHelper;

    /** The Constant log. */
    private static final Logger log = Logger.getLogger(ProcessSwitchDetails.class);

    /**
     * get All SwitchList.
     * 
     * @return SwitchRelationData
     */
    public SwitchRelationData getswitchdataList() {

        ObjectMapper mapper = new ObjectMapper();
        SwitchRelationData switchRelationData = new SwitchRelationData();
        List<SwitchInfo> switchInfoList = new ArrayList<SwitchInfo>();
        List<SwitchResponse> switchResponseList = null;

        try {
            switchResponseList = apiHelper.getSwitchList(IConstants.GET_SWITCH_DATA_URL);
        } catch (Exception exception) {
            log.fatal("Fatal Exception in getswitchdataList "
                    + ExceptionUtils.getFullStackTrace(exception));
        }
        log.info("getListofswitchLinkdata post api call response is " + switchResponseList);

        if (switchResponseList != null) {
            List<SwitchResponse> list =
                    mapper.convertValue(switchResponseList,
                            new TypeReference<List<SwitchResponse>>() {});
            for (SwitchResponse switchResponse : list) {
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
        return switchRelationData;
    }

    /**
     * get All Links.
     *
     * @param switchRelationData the switch relation data
     * @return SwitchRelationData
     */
    public SwitchRelationData getAllLinks(SwitchRelationData switchRelationData) {

        ObjectMapper mapper = new ObjectMapper();
        if (switchRelationData == null)
            switchRelationData = new SwitchRelationData();
        List<Switchrelation> switchrelationList = new ArrayList<Switchrelation>();
        List<LinkResponse> linkResponseList = null;
        try {
            linkResponseList = apiHelper.getLinkList(IConstants.GET_LINK_DATA_URL);
        } catch (Exception exception) {
            log.error("Fatal IOException in getAllLinks " + exception.getMessage());
        }
        log.info("getAllLinks api call response is " + linkResponseList);

        if (linkResponseList != null) {
            List<LinkResponse> list =
                    mapper.convertValue(linkResponseList,
                            new TypeReference<List<LinkResponse>>() {});

            for (LinkResponse linkResponse : list) {
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
        return switchRelationData;
    }

    /**
     * get All Flows.
     *
     * @param switchRelationData the switch relation data
     * @return SwitchRelationData
     */
    public SwitchRelationData getAllFlows(SwitchRelationData switchRelationData) {

        ObjectMapper mapper = new ObjectMapper();
        if (switchRelationData == null)
            switchRelationData = new SwitchRelationData();
        List<FlowPath> flowPathList = new ArrayList<FlowPath>();
        List<FlowResponse> flowResponseList = new ArrayList<FlowResponse>();
        try {
            flowPathList = apiHelper.getFlowList(IConstants.GET_FLOW_DATA_URL);

        } catch (Exception e) {

        }
        if (flowPathList != null) {
            List<FlowPath> list =
                    mapper.convertValue(flowPathList, new TypeReference<List<FlowPath>>() {});
            for (FlowPath flowPath : list) {
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
                String status = "";
                try {
                    status =
                            APIHelper.getFlowStatus(IConstants.GET_FLOW_STATUS_URL,
                                    flowPath.getFlowid());
                } catch (Exception e) {

                }
                flowRsponse.setStatus(status);
                flowResponseList.add(flowRsponse);
            }
        }

        switchRelationData.setFlowResponse(flowResponseList);
        return switchRelationData;
    }

    /**
     * get All Ports.
     * 
     * @return List<PortInfo>
     */
    public List<PortInfo> getPortResponse() {

        log.info("inside getPortResponse");
        APIHelper apiHelper = new APIHelper();
        PortResponse portResponse = null;
        List<PortInfo> switchPortsInfoList = new ArrayList<PortInfo>();
        List<PortModel> portModelList = new ArrayList<PortModel>();

        try {
            portResponse = apiHelper.getPortResponse(IConstants.GET_PORT_DATA_URL);

        } catch (Exception exception) {
            log.fatal("Fatal Exception in getPortResponse "
                    + ExceptionUtils.getFullStackTrace(exception));
        }
        log.info("getPortResponse GET_PORT_DATA_URL api call response is " + portResponse);

        if (portResponse != null) {
            List<PortSwitch> switchList = portResponse.getSwitches();
            portModelList = SwitchDataUtil.getPortModelList(switchList);

            try {
                JSONObject jsonObject =
                        apiHelper.getPortJsonResponse(IConstants.GET_SWITCH_PORT_DATA_URL);

                log.info("getPortResponse GET_SWITCH_PORT_DATA_URL api call response is "
                        + jsonObject);
                ObjectMapper mapper = new ObjectMapper();
                Set<?> keySet = jsonObject.keySet();
                Iterator<?> itr = keySet.iterator();

                while (itr.hasNext()) {
                    String key = (String) itr.next();
                    Object object = jsonObject.get(key);
                    String val = object.toString();
                    PortDetailResponse portDetailResponse =
                            mapper.readValue(val, PortDetailResponse.class);
                    List<PortDesc> portDescList = portDetailResponse.getPortDesc();

                    if (portDescList != null) {

                        switchPortsInfoList =
                                SwitchDataUtil.getSwitchPortInfo(portDescList, key, portModelList,
                                        switchPortsInfoList);
                    }
                }
            } catch (Exception exception) {
                log.fatal("Fatal Exception in getPortResponse "
                        + ExceptionUtils.getFullStackTrace(exception));
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
    public static List<PortInfo> getPortResponseBasedOnSwitchId(String switchId) {

        List<PortInfo> response = new ArrayList<PortInfo>();
        ProcessSwitchDetails processSwitchDetails = new ProcessSwitchDetails();
        List<PortInfo> portdetailList = processSwitchDetails.getPortResponse();
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
        return response;
    }

}
