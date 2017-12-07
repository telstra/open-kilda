package org.openkilda.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.openkilda.model.Port;
import org.openkilda.model.PortDesc;
import org.openkilda.model.PortInfo;
import org.openkilda.model.PortInterface;
import org.openkilda.model.PortModel;
import org.openkilda.model.PortSwitch;
import org.openkilda.ws.response.PortResponseData;
import org.springframework.stereotype.Component;

/**
 * The Class SwitchDataUtil.
 *
 * @author Gaurav Chugh
 */
@Component
public class SwitchDataUtil {

    /** The Constant log. */
    private final Logger log = Logger.getLogger(SwitchDataUtil.class);

    /**
     * Gets the switch id.
     *
     * @param portSwitch the port switch
     * @return the switch id
     */
    public String getSwitchId(PortSwitch portSwitch) {

        log.info("inside getSwitchId to convert dpid into switchid format.");
        String switchId = "";
        int a = 0;
        int b = 2;
        String key1 = portSwitch.getDpid();
        for (int i = 0; i < key1.length() / 2; i++) {
            String val = key1.substring(a, b).toLowerCase();
            a = a + 2;
            b = b + 2;
            switchId = switchId + val + ":";
        }

        switchId = switchId.substring(0, switchId.length() - 1);
        log.info("getSwitchId exit");
        return switchId;
    }

    /**
     * Gets the switch port info.
     *
     * @param portDescList the port desc list
     * @param key the key
     * @param portModelList the port model list
     * @param switchPortsInfoList the switch ports info list
     * @return the switch port info
     */
    public List<PortInfo> getSwitchPortInfo(List<PortDesc> portDescList, String key,
            List<PortModel> portModelList, List<PortInfo> switchPortsInfoList) {

        log.info("inside getSwitchPortInfo to return portInfoList.");
        for (PortDesc portDesc : portDescList) {
            setPortInfo(key, portModelList, switchPortsInfoList, portDesc);
        }
        log.info("getSwitchPortInfo exit");
        return switchPortsInfoList;
    }

    /**
     * Sets the port info.
     *
     * @param key the key
     * @param portModelList the port model list
     * @param switchPortsInfoList the switch ports info list
     * @param portDesc the port desc
     */
    private void setPortInfo(String key, List<PortModel> portModelList,
            List<PortInfo> switchPortsInfoList, PortDesc portDesc) {

        log.info("Inside setPortInfo .");
        if (portDesc.getCurrentFeatures().size() != 0) {
            PortInfo portInfo = new PortInfo();
            String first = portDesc.getCurrentFeatures().get(0);
            String second = portDesc.getCurrentFeatures().get(1);
            String comma = ",";
            String currentFeatures = first + comma + second;
            String portNameDesc = portDesc.getName();
            portInfo.setSwitchName(key);

            if (portModelList != null) {
                for (PortModel portModel : portModelList) {
                    String switchId = portModel.getSwitchId();
                    String portName = portModel.getPortName();
                    if (switchId.equalsIgnoreCase(key) && portName.equalsIgnoreCase(portNameDesc)) {
                        portInfo.setStatus(portModel.getStatus());
                        break;
                    }

                }
            }

            portInfo.setInterfacetype(currentFeatures);
            portInfo.setPortNumber(portDesc.getPortNumber());
            portInfo.setPortName(portDesc.getName());
            switchPortsInfoList.add(portInfo);
            Collections.sort(switchPortsInfoList);
            log.info("exit setPortInfo .");
        }
    }

    /**
     * Gets the port model list.
     *
     * @param switchList the switch list
     * @return the port model list
     */
    public List<PortModel> getPortModelList(List<PortSwitch> switchList) {

        log.info("Inside getPortModelList .");
        List<PortModel> portModelList = new ArrayList<PortModel>();
        for (PortSwitch portSwitch : switchList) {
            String switchId = getSwitchId(portSwitch);
            List<PortInterface> interfaceList = portSwitch.getInterface();

            if (interfaceList != null) {
                for (PortInterface portInterface : interfaceList) {
                    PortModel portModel = new PortModel();
                    portModel.setSwitchId(switchId);
                    portModel.setPortName(portInterface.getName());
                    portModel.setStatus(portInterface.getStatus());
                    portModelList.add(portModel);
                }
            }
        }
        log.info("exit getPortModelList .");
        return portModelList;
    }

    /**
     * Gets the switch port info with switch id.
     *
     * @param portDescList the port desc list
     * @param key the key
     * @param portModelList the port model list
     * @param portResponseDataList the port response data list
     * @return the switch port info with switch id
     */
    public List<PortResponseData> getSwitchPortInfoWithSwitchId(List<PortDesc> portDescList,
            String key, List<PortModel> portModelList, List<PortResponseData> portResponseDataList) {

        log.info("inside getSwitchPortInfoWithSwitchId to return portInfoList.");
        List<Port> portList = new ArrayList<Port>();
        PortResponseData portResponseData = new PortResponseData();

        for (PortDesc portDesc : portDescList) {

            setPort(key, portModelList, portList, portResponseData, portDesc);
        }
        portResponseDataList.add(portResponseData);
        log.info("getSwitchPortInfoWithSwitchId exit");
        return portResponseDataList;
    }

    /**
     * Sets the port.
     *
     * @param key the key
     * @param portModelList the port model list
     * @param portList the port list
     * @param portResponseData the port response data
     * @param portDesc the port desc
     */
    private void setPort(String key, List<PortModel> portModelList, List<Port> portList,
            PortResponseData portResponseData, PortDesc portDesc) {

        log.info("Inside setPort .");
        if (portDesc.getCurrentFeatures().size() != 0) {
            Port port = new Port();

            String first = portDesc.getCurrentFeatures().get(0);
            String second = portDesc.getCurrentFeatures().get(1);
            String comma = ",";
            String currentFeatures = first + comma + second;
            String portNameDesc = portDesc.getName();
            portResponseData.setName(key);

            if (portModelList != null) {
                for (PortModel portModel : portModelList) {
                    String switchId = portModel.getSwitchId();
                    String portName = portModel.getPortName();
                    if (switchId.equalsIgnoreCase(key) && portName.equalsIgnoreCase(portNameDesc)) {
                        port.setStatus(portModel.getStatus());
                        break;
                    }
                }
            }
            port.setInterfacetype(currentFeatures);
            port.setPortNumber(portDesc.getPortNumber());
            port.setPortName(portDesc.getName());
            portList.add(port);
            Collections.sort(portList);
            portResponseData.setPorts(portList);
            log.info("exit setPort .");
        }
    }

}
