package org.openkilda.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

import org.apache.http.HttpResponse;
import org.json.simple.JSONObject;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.converter.IslLinkConverter;
import org.openkilda.integration.converter.PortConverter;
import org.openkilda.integration.model.response.IslLink;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtils;
import org.openkilda.utility.JsonUtil;
import org.openkilda.utility.Util;

/**
 * The Class SwitchIntegrationService.
 *
 * @author Gaurav Chugh
 */
@Service
public class SwitchIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchIntegrationService.class);

    @Autowired
    private RestClientManager restClientManager;

    @Autowired
    private ApplicationProperties applicationProperties;
    
    @Autowired
    private Util util;

    /**
     * Gets the switches.
     *
     * @return the switches
     */
    public List<SwitchInfo> getSwitches() {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getSwitches(),
                    HttpMethod.GET, "", "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                List<SwitchInfo> switchesResponse =
                        restClientManager.getResponseList(response, SwitchInfo.class);
                return switchesResponse;
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getswitchdataList " + exception.getMessage());
        }
        return null;
    }


    /**
     * Gets the isl links.
     *
     * @return the isl links
     */
    public List<IslLinkInfo> getIslLinks() {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getLinks(),
                    HttpMethod.GET, "", "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                List<IslLink> links = restClientManager.getResponseList(response, IslLink.class);
                return IslLinkConverter.toIslLinksInfo(links);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getIslLinks " + exception.getMessage());
        }
        return null;
    }

    /**
     * Gets the switch ports.
     *
     * @return the switch ports
     */
    public List<PortInfo> getSwitchPorts(final String switchId) {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getSwitchPorts(),
                    HttpMethod.GET, "", "", "");
            if (RestClientManager.isValidResponse(response)) {
                String responseEntity = IoUtils.toString(response.getEntity().getContent());
                JSONObject jsonObject = JsonUtil.toObject(responseEntity, JSONObject.class);
                return PortConverter.toPortsInfo(jsonObject, switchId);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getSwitchPorts " + exception.getMessage());
        }
        return null;
    }

}
