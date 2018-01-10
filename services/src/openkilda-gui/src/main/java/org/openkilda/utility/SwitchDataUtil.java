package org.openkilda.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.openkilda.integration.model.response.Port;
import org.openkilda.integration.model.response.PortDesc;
import org.openkilda.integration.model.response.PortModel;
import org.openkilda.integration.model.response.PortResponseData;
import org.openkilda.model.response.PortInfo;
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
	 * Gets the switch port info.
	 *
	 * @param portDescList
	 *            the port desc list
	 * @param key
	 *            the key
	 * @param portModelList
	 *            the port model list
	 * @param switchPortsInfoList
	 *            the switch ports info list
	 * @return the switch port info
	 */
	public List<PortInfo> getSwitchPortInfo(List<PortDesc> portDescList,
			String key, List<PortInfo> switchPortsInfoList) {

		log.info("inside getSwitchPortInfo to return portInfoList.");
		for (PortDesc portDesc : portDescList) {

			if (!portDesc.getPortNumber().equalsIgnoreCase("local")) {
				PortInfo info = setPortInfo(key, portDesc);
				switchPortsInfoList.add(info);
			}

		}
		Collections.sort(switchPortsInfoList);
		log.info("getSwitchPortInfo exit");
		return switchPortsInfoList;
	}

	/**
	 * Sets the port info.
	 *
	 * @param key
	 *            the key
	 * @param portModelList
	 *            the port model list
	 * @param portDesc
	 *            the port desc
	 */
	private PortInfo setPortInfo(String key, PortDesc portDesc) {

		log.info("Inside setPortInfo .");
		PortInfo portInfo = new PortInfo();
		StringBuilder currentFeatures = new StringBuilder();
		
		if (portDesc.getCurrentFeatures() != null
				&& portDesc.getCurrentFeatures().size() > 0) {
			
			for (int i = 0; i < portDesc.getCurrentFeatures().size(); i++) {
				if (currentFeatures.length() == 0) {
					currentFeatures = currentFeatures.append(portDesc
							.getCurrentFeatures().get(i));
				} else {
					currentFeatures = currentFeatures.append(","+portDesc
							.getCurrentFeatures().get(i));
				}
			}
			portInfo.setInterfacetype(currentFeatures.toString());
		}
		portInfo.setSwitchName(key);
		portInfo.setPortNumber(portDesc.getPortNumber());
		portInfo.setPortName(portDesc.getName());
		if (portDesc.getState() != null && !portDesc.getState().isEmpty())
			portInfo.setStatus(portDesc.getState().get(0));

		log.info("exit setPortInfo .");
		return portInfo;

	}

	/**
	 * Gets the switch port info with switch id.
	 *
	 * @param portDescList
	 *            the port desc list
	 * @param key
	 *            the key
	 * @param portModelList
	 *            the port model list
	 * @param portResponseDataList
	 *            the port response data list
	 * @return the switch port info with switch id
	 */
	public List<PortResponseData> getSwitchPortInfoWithSwitchId(
			List<PortDesc> portDescList, String key,
			List<PortModel> portModelList,
			List<PortResponseData> portResponseDataList) {

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
	 * @param key
	 *            the key
	 * @param portModelList
	 *            the port model list
	 * @param portList
	 *            the port list
	 * @param portResponseData
	 *            the port response data
	 * @param portDesc
	 *            the port desc
	 */
	private void setPort(String key, List<PortModel> portModelList,
			List<Port> portList, PortResponseData portResponseData,
			PortDesc portDesc) {

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
					if (switchId.equalsIgnoreCase(key)
							&& portName.equalsIgnoreCase(portNameDesc)) {
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
