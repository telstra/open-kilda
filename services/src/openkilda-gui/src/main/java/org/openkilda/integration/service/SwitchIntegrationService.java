/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.integration.service;

import org.openkilda.constants.IConstants;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.converter.IslLinkConverter;
import org.openkilda.integration.exception.ContentNotFoundException;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.integration.model.response.IslLink;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchInfo;
import org.openkilda.service.ApplicationService;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;

import java.io.UnsupportedEncodingException;

import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private IslLinkConverter islLinkConverter;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Gets the switches.
     *
     * @return the switches
     */
    public List<SwitchInfo> getSwitches() {
        HttpResponse response = restClientManager.invoke(
                applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_SWITCHES, HttpMethod.GET, "", "",
                applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            List<SwitchInfo> switchesResponse = restClientManager.getResponseList(response, SwitchInfo.class);
            return getSwitchInfoSetName(switchesResponse);
        }
        return null;
    }

    /**
     * Gets the switch info set name.
     *
     * @param switches the switches
     * @return the switch info set name
     */
    private List<SwitchInfo> getSwitchInfoSetName(List<SwitchInfo> switches) {

        LOGGER.info("Inside getSwitchInfoSetName : Start");
        if (switches != null && !StringUtils.isEmpty(switches)) {

            Map<String, String> csNames = getCustomSwitchNameFromFile();

            for (SwitchInfo switchInfo : switches) {
                switchInfo.setName(customSwitchName(csNames, switchInfo.getSwitchId()));
            }
        }
        return switches;
    }

    /**
     * Custom switch name.
     *
     * @param csNames the cs names
     * @param switchId the switch id
     * @return the string
     */
    public String customSwitchName(Map<String, String> csNames, String switchId) {
        if (csNames != null && !StringUtils.isEmpty(csNames) && csNames.size() > 0) {
            if (csNames.containsKey(switchId.toLowerCase())
                    || csNames.containsKey(switchId.toUpperCase())) {
                if (!IoUtil.chkStringIsNotEmpty(csNames.get(switchId))) {
                    return switchId;
                } else {
                    return csNames.get(switchId);
                }
            } else {
                return switchId;
            }
        } else {
            return switchId;
        }
    }

    /**
     * Gets the isl links.
     *
     * @return the isl links
     */
    public List<IslLinkInfo> getIslLinks() {
        List<IslLink> links = getIslLinkPortsInfo();
        if (CollectionUtil.isEmpty(links)) {
            throw new ContentNotFoundException();
        }
        return islLinkConverter.toIslLinksInfo(links, islCostMap());
    }
    
    /**
     * Gets the isl links port info.
     *
     * @return the isl links port info
     */
    public List<IslLink> getIslLinkPortsInfo() {
        HttpResponse response = restClientManager.invoke(
                applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINKS, HttpMethod.GET, "", "",
                applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            List<IslLink> links = restClientManager.getResponseList(response, IslLink.class);
            return links;
        }
        return null;
    }
    
    private Map<String, String> islCostMap() {
        List<LinkProps> linkProps = getIslLinkProps(null);
        Map<String, String> islCostMap = new HashMap<>();
        if (linkProps != null) {

            linkProps.forEach(linkProp -> {
                String key = linkProp.getSrcSwitch() + "-" + linkProp.getSrcPort() + "-" + linkProp.getDstSwitch() + "-"
                        + linkProp.getDstPort();
                String value = linkProp.getProperty("cost");
                islCostMap.put(key, value);
            });
        }

        return islCostMap;

    }
    

    /**
     * Gets the isl link cost.
     *
     * @return the isl link cost
     */
    public List<LinkProps> getIslLinkProps(LinkProps keys) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_PROPS);
        builder = setLinkProps(keys, builder);
        String fullUri = builder.build().toUriString();
        HttpResponse response = restClientManager.invoke(fullUri, HttpMethod.GET, "", "",
                applicationService.getAuthHeader());
        try {
            if (RestClientManager.isValidResponse(response)) {
                List<LinkProps> linkPropsResponses = restClientManager.getResponseList(response, LinkProps.class);
                if (!CollectionUtil.isEmpty(linkPropsResponses)) {
                    return linkPropsResponses;
                }
            }
        } catch (InvalidResponseException exception) {
            LOGGER.error("Exception in getIslLinkProps " + exception.getMessage(), exception);
            return null;
        }
        return null;
    }


    /**
     * Get custom switch name from file.
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getCustomSwitchNameFromFile() {
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
            LOGGER.error(
                    "Inside getCustomSwitchNameFromFile unable to find switch file path Exception :",
                    ex);
        }
        return csNames;

    }

    /**
     * Update isl link props.
     *
     * @param keys the keys
     * @return the string
     */
    public String updateIslLinkProps(List<LinkProps> keys) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_PROPS, HttpMethod.PUT,
                    objectMapper.writeValueAsString(keys), "application/json", applicationService.getAuthHeader());
            return IoUtil.toString(response.getEntity().getContent());
        } catch (Exception e) {
            LOGGER.error("Inside updateIslLinkProps  Exception :", e);
            throw new IntegrationException(e);
        }
    }

    /**
     * This Method is used to set link props.
     * 
     * @param keys th link properties
     * @param builder the uri component builder
     * @return UriComponentsBuilder
     */
    private UriComponentsBuilder setLinkProps(LinkProps keys, UriComponentsBuilder builder) {
        try {
            if (keys != null) {
                if (!keys.getSrcSwitch().isEmpty()) {
                    builder.queryParam("src_switch",
                            URLEncoder.encode(keys.getSrcSwitch(), "UTF-8"));
                }
                if (!keys.getSrcPort().isEmpty()) {
                    builder.queryParam("src_port", URLEncoder.encode(keys.getSrcPort(), "UTF-8"));
                }
                if (!keys.getDstSwitch().isEmpty()) {
                    builder.queryParam("dst_switch",
                            URLEncoder.encode(keys.getDstSwitch(), "UTF-8"));
                }
                if (!keys.getDstPort().isEmpty()) {
                    builder.queryParam("dst_port", URLEncoder.encode(keys.getDstPort(), "UTF-8"));
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new ContentNotFoundException();
        }
        return builder;
    }
    
    /**
     * This Method is used to get switch rules.
     * 
     * @param switchId the switch id
     * @return the switch rules
     */
    public String getSwitchRules(String switchId) {

        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl()
                            + IConstants.NorthBoundUrl.GET_SWITCH_RULES.replace("{switch_id}", switchId),
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
            return IoUtil.toString(response.getEntity().getContent());
        } catch (Exception e) {
            LOGGER.error("Inside updateIslLinkProps  Exception :", e);
            throw new IntegrationException(e);
        }
    }
    
    /**
     * Configure port.
     *
     * @param switchId the switch id
     * @param port the port
     * @param configuration the configuration
     * @return the configured port
     */
    public ConfiguredPort configurePort(String switchId, String port, PortConfiguration configuration) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.CONFIG_SWITCH_PORT
                            .replace("{switch_id}", switchId).replace("{port_no}", port),
                    HttpMethod.PUT, objectMapper.writeValueAsString(configuration), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, ConfiguredPort.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Inside configurePort  Exception :", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while converting configration to string. Exception: " + e.getMessage(), e);
            throw new IntegrationException(e);
        }
        return null;
    }
}
