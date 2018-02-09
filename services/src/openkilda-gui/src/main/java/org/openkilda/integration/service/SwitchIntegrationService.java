package org.openkilda.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.json.simple.JSONObject;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.converter.IslLinkConverter;
import org.openkilda.integration.converter.PortConverter;
import org.openkilda.integration.exception.ContentNotFoundException;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.model.response.IslLink;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;
import org.openkilda.service.ApplicationService;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.JsonUtil;

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
    private ApplicationService applicationService;

    /**
     * Gets the switches.
     *
     * @return the switches
     * @throws IntegrationException
     */
    public List<SwitchInfo> getSwitches() {
        HttpResponse response =
                restClientManager.invoke(applicationProperties.getSwitches(), HttpMethod.GET, "",
                        "", applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            List<SwitchInfo> switchesResponse =
                    restClientManager.getResponseList(response, SwitchInfo.class);
            return getSwitchInfoSetName(switchesResponse);
        }
        return null;
    }

    /**
     * Gets the SwitchInfoSetName.
     *
     * @return the switches
     * @throws IntegrationException
     */
    private List<SwitchInfo> getSwitchInfoSetName(List<SwitchInfo> switches) {

        LOGGER.info("Inside getSwitchInfoSetName : Start");
        if (switches != null && !StringUtils.isEmpty(switches)) {

            Map<String, String> csNames = getCustomSwitchNameFromFile();

            for (SwitchInfo switchInfo : switches) {

                String switchId = switchInfo.getSwitchId();

                if (csNames != null && !StringUtils.isEmpty(csNames) && csNames.size() > 0) {

                    if (csNames.containsKey(switchId)) {
                        if (!IoUtil.chkStringIsNotEmpty(csNames.get(switchId))) {
                            switchInfo.setName(switchId);
                        } else {
                            switchInfo.setName(csNames.get(switchId));
                        }
                    } else {
                        switchInfo.setName(switchId);
                    }
                } else
                    switchInfo.setName(switchId);
            }
        }
        return switches;
    }

    /**
     * Gets the isl links.
     *
     * @return the isl links
     */
    public List<IslLinkInfo> getIslLinks() {
        HttpResponse response =
                restClientManager.invoke(applicationProperties.getLinks(), HttpMethod.GET, "", "",
                        applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            List<IslLink> links = restClientManager.getResponseList(response, IslLink.class);
            if (CollectionUtil.isEmpty(links)) {
                throw new ContentNotFoundException();
            }
            return IslLinkConverter.toIslLinksInfo(links);
        }
        return null;
    }

    /**
     * Gets the switch ports.
     *
     * @return the switch ports
     * @throws IntegrationException
     */
    public List<PortInfo> getSwitchPorts(final String switchId) throws IntegrationException {
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getSwitchPorts(),
                            HttpMethod.GET, "", "", "");
            if (RestClientManager.isValidResponse(response)) {
                String responseEntity = IoUtil.toString(response.getEntity().getContent());
                JSONObject jsonObject = JsonUtil.toObject(responseEntity, JSONObject.class);
                return PortConverter.toPortsInfo(jsonObject, switchId);
            }
        } catch (IOException exception) {
            LOGGER.error("Exception in getSwitchPorts " + exception.getMessage(), exception);
            throw new IntegrationException(exception);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getCustomSwitchNameFromFile() {
        Map<String, String> csNames = new HashMap<String, String>();

        InputStream inputStream = null;
        String data = null;
        try {
            inputStream = new URL(applicationProperties.getSwitchDataFilePath()).openStream();
            if (inputStream != null) {
                data = IoUtil.toString(inputStream);

                if (data != null && !StringUtils.isEmpty(data)) {
                    csNames = JsonUtil.toObject(data, HashMap.class);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Inside getSwitchInfoSetName unable to find switch file path Exception :",
                    ex);
        }
        return csNames;

    }
}
