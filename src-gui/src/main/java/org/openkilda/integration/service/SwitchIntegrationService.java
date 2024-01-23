/* Copyright 2024 Telstra Open Source
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

import static org.openkilda.integration.converter.FlowConverter.toFlowV2InfosPerPorts;
import static org.openkilda.integration.converter.FlowConverter.toFlowsInfo;
import static org.openkilda.integration.converter.IslLinkConverter.toIslLinksInfo;
import static org.openkilda.utility.SwitchUtil.customSwitchName;

import org.openkilda.config.ApplicationProperties;
import org.openkilda.constants.IConstants;
import org.openkilda.constants.IConstants.ApplicationSetting;
import org.openkilda.constants.IConstants.StorageType;
import org.openkilda.dao.entity.SwitchNameEntity;
import org.openkilda.dao.repository.SwitchNameRepository;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.exception.ContentNotFoundException;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.integration.model.response.IslLink;
import org.openkilda.integration.model.response.SwitchFlowsPerPort;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkBfdProperties;
import org.openkilda.model.LinkMaxBandwidth;
import org.openkilda.model.LinkParametersDto;
import org.openkilda.model.LinkProps;
import org.openkilda.model.LinkUnderMaintenanceDto;
import org.openkilda.model.SwitchFlowsInfoPerPort;
import org.openkilda.model.SwitchInfo;
import org.openkilda.model.SwitchLocation;
import org.openkilda.model.SwitchLogicalPort;
import org.openkilda.model.SwitchMeter;
import org.openkilda.model.SwitchProperty;
import org.openkilda.service.ApplicationService;
import org.openkilda.service.ApplicationSettingService;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
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
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationSettingService applicationSettingService;

    @Autowired
    private SwitchNameRepository switchNameRepository;

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
     * Get the switch by id.
     *
     * @return the switch
     */
    public SwitchInfo getSwitchesById(final String switchId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl()
                            + IConstants.NorthBoundUrl.GET_SWITCH.replace("{switch_id}", switchId),
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                SwitchInfo switchInfo = restClientManager.getResponse(response, SwitchInfo.class);
                if (switchInfo != null) {
                    switchInfo.setName(customSwitchName(getSwitchNames(), switchInfo.getSwitchId()));
                    switchInfo.setControllerSwitch(true);
                    return switchInfo;
                }
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting switch by id:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Gets the switch info set name.
     *
     * @param switches the switches
     * @return the switch info set name
     */
    private List<SwitchInfo> getSwitchInfoSetName(final List<SwitchInfo> switches) {
        if (CollectionUtils.isNotEmpty(switches)) {
            Map<String, String> csNames = getSwitchNames();
            for (SwitchInfo switchInfo : switches) {
                switchInfo.setName(customSwitchName(csNames, switchInfo.getSwitchId()));
                switchInfo.setControllerSwitch(true);
            }
        }
        return switches;
    }

    /**
     * Gets the switch names.
     *
     * @return the switch names
     */
    public Map<String, String> getSwitchNames() {
        Map<String, String> csNames = new HashMap<String, String>();
        if (IConstants.STORAGE_TYPE_FOR_SWITCH_NAME == null) {
            String value = applicationSettingService.getApplicationSetting(ApplicationSetting.SWITCH_NAME_STORAGE_TYPE);
            IConstants.STORAGE_TYPE_FOR_SWITCH_NAME = StorageType.get(value);
        }

        if (IConstants.STORAGE_TYPE_FOR_SWITCH_NAME == StorageType.FILE_STORAGE) {
            csNames = getCustomSwitchNameFromFile();
        } else if (IConstants.STORAGE_TYPE_FOR_SWITCH_NAME == StorageType.DATABASE_STORAGE) {
            csNames = getCustomSwitchNameFromDatabase();
        }
        return csNames;
    }

    /**
     * Gets the isl links.
     *
     * @return the isl links
     */
    public List<IslLinkInfo> getIslLinks(final LinkProps keys) {
        List<IslLink> links = getIslLinkPortsInfo(keys);
        if (CollectionUtil.isEmpty(links)) {
            throw new ContentNotFoundException();
        }

        return toIslLinksInfo(links, islCostMap(null), getSwitchNames());
    }

    /**
     * Gets the isl links port info.
     *
     * @return the isl links port info
     */
    public List<IslLink> getIslLinkPortsInfo(final LinkProps keys) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINKS);
        setLinkProps(keys, builder);
        String fullUri = builder.build().toUriString();
        HttpResponse response = restClientManager.invoke(fullUri, HttpMethod.GET, "", "",
                applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            return restClientManager.getResponseList(response, IslLink.class);
        }
        return null;
    }

    private Map<String, String> islCostMap(final LinkProps keys) {
        List<LinkProps> linkProps = getIslLinkProps(keys);
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
    public List<LinkProps> getIslLinkProps(final LinkProps keys) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_PROPS);
        setLinkProps(keys, builder);
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
        } catch (InvalidResponseException e) {
            LOGGER.warn("Error occurred while getting isl link props ", e);
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
        } catch (IOException e) {
            LOGGER.warn("Error occurred while getting switch name from file", e);
        }
        return csNames;

    }

    /**
     * Gets the custom switch name from database.
     *
     * @return the custom switch name from database
     */
    public Map<String, String> getCustomSwitchNameFromDatabase() {
        Map<String, String> csNames = new HashMap<String, String>();
        List<SwitchNameEntity> switchNames = switchNameRepository.findAll();
        for (SwitchNameEntity name : switchNames) {
            csNames.put(name.getSwitchDpid(), name.getSwitchName());
        }
        return csNames;
    }

    /**
     * Update isl link props.
     *
     * @param keys the keys
     * @return the string
     */
    public String updateIslLinkProps(final List<LinkProps> keys) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_PROPS, HttpMethod.PUT,
                    objectMapper.writeValueAsString(keys), "application/json", applicationService.getAuthHeader());
            return IoUtil.toString(response.getEntity().getContent());
        } catch (IOException e) {
            LOGGER.warn("Error occurred while updating isl link props", e);
            throw new IntegrationException(e);
        }
    }

    /**
     * This Method is used to set link props.
     *
     * @param keys    th link properties
     * @param builder the uri component builder
     * @return UriComponentsBuilder
     */
    private UriComponentsBuilder setLinkProps(final LinkProps keys, final UriComponentsBuilder builder) {
        try {
            if (keys != null) {
                if (!keys.getSrcSwitch().isEmpty()) {
                    builder.queryParam("src_switch", URLEncoder.encode(keys.getSrcSwitch(), "UTF-8"));
                }
                if (!keys.getSrcPort().isEmpty()) {
                    builder.queryParam("src_port", URLEncoder.encode(keys.getSrcPort(), "UTF-8"));
                }
                if (!keys.getDstSwitch().isEmpty()) {
                    builder.queryParam("dst_switch", URLEncoder.encode(keys.getDstSwitch(), "UTF-8"));
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
    public String getSwitchRules(final String switchId) {

        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl()
                            + IConstants.NorthBoundUrl.GET_SWITCH_RULES.replace("{switch_id}", switchId),
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
            return IoUtil.toString(response.getEntity().getContent());
        } catch (IOException e) {
            LOGGER.error("Error occurred while retrivig switch rules by switch id:" + switchId, e);
            throw new IntegrationException(e);
        }
    }

    /**
     * Configure port.
     *
     * @param switchId      the switch id
     * @param port          the port
     * @param configuration the configuration
     * @return the configured port
     */
    public ConfiguredPort configurePort(final String switchId, final String port,
                                        final PortConfiguration configuration) {
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
            LOGGER.error("Error occurred while configuring port. Switch Id:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while converting configration to string. Switch Id:" + switchId, e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Gets the isl flows.
     *
     * @param srcSwitch the source switch
     * @param srcPort   the source port
     * @param dstSwitch the destination switch
     * @param dstPort   the destination port
     * @return the isl flows
     */
    public List<FlowInfo> getIslFlows(final String srcSwitch, final String srcPort, final String dstSwitch,
                                      final String dstPort) {
        List<Flow> flowList = getIslFlowList(srcSwitch, srcPort, dstSwitch, dstPort);
        if (flowList != null) {
            return toFlowsInfo(flowList, getSwitchNames());
        }
        return new ArrayList<>();
    }

    /**
     * Gets the isl flow list.
     *
     * @param srcSwitch the source switch
     * @param srcPort   the source port
     * @param dstSwitch the destination switch
     * @param dstPort   the destination port
     * @return the isl flow list
     */
    public List<Flow> getIslFlowList(final String srcSwitch, final String srcPort, final String dstSwitch,
                                     final String dstPort) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_ISL_FLOW
                            .replace("{src_switch}", srcSwitch).replace("{src_port}", srcPort)
                            .replace("{dst_switch}", dstSwitch).replace("{dst_port}", dstPort),
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, Flow.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting isl flow list", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Gets the meters.
     *
     * @return the meters
     */
    public SwitchMeter getMeters(final String switchId) {
        SwitchMeter switchesResponse = null;
        HttpResponse response = restClientManager.invoke(
                applicationProperties.getNbBaseUrl()
                        + IConstants.NorthBoundUrl.GET_SWITCH_METERS.replace("{switch_id}", switchId),
                HttpMethod.GET, "", "", applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            switchesResponse = restClientManager.getResponse(response, SwitchMeter.class);
        }
        return switchesResponse;
    }

    /**
     * Updates the switch maintenance.
     *
     * @return the switch info
     */
    public SwitchInfo updateMaintenanceStatus(String switchId, SwitchInfo switchInfo) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                            .UPDATE_SWITCH_UNDER_MAINTENANCE.replace("{switch_id}", switchId),
                    HttpMethod.POST, objectMapper.writeValueAsString(switchInfo), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchInfo.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while updating switch:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.warn("Error occurred while updating switch:" + switchId, e);
            throw new IntegrationException(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Gets the isl links.
     *
     * @return the isl links info
     */
    public List<IslLinkInfo> updateIslLinks(final LinkUnderMaintenanceDto linkUnderMaintenanceDto) {
        List<IslLink> links = updateIslLinkMaintenanceStatus(linkUnderMaintenanceDto);
        if (CollectionUtil.isEmpty(links)) {
            throw new ContentNotFoundException();
        }
        return toIslLinksInfo(links, islCostMap(null), getSwitchNames());
    }

    /**
     * Updates the isl links.
     *
     * @return the isl links
     */
    public List<IslLink> updateIslLinkMaintenanceStatus(final LinkUnderMaintenanceDto islLinkInfo) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.UPDATE_LINK_MAINTENANCE,
                    HttpMethod.PATCH,
                    objectMapper.writeValueAsString(islLinkInfo),
                    "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, IslLink.class);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while updating link", e);
        }
        return null;
    }

    /**
     * Deletes the isl.
     *
     * @return the IslLinkInfo
     */
    public List<IslLinkInfo> deleteLink(LinkParametersDto linkParametersDto) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.DELETE_LINK, HttpMethod.DELETE,
                    objectMapper.writeValueAsString(linkParametersDto), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, IslLinkInfo.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while deleting link", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while deleting link", e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Updates the isl max bandwidth.
     *
     * @return the LinkMaxBandwidth
     */
    public LinkMaxBandwidth updateLinkBandwidth(String srcSwitch, String srcPort, String dstSwitch, String dstPort,
                                                LinkMaxBandwidth linkMaxBandwidth) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.UPDATE_LINK_BANDWIDTH
                            .replace("{src_switch}", srcSwitch).replace("{src_port}", srcPort)
                            .replace("{dst_switch}", dstSwitch).replace("{dst_port}", dstPort), HttpMethod.PATCH,
                    objectMapper.writeValueAsString(linkMaxBandwidth), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, LinkMaxBandwidth.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while updating link bandwidth", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while updating link bandwidth", e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Gets the switch flows.
     *
     * @return the FlowInfo
     */
    public List<FlowInfo> getSwitchFlows(String switchId, String port) {
        List<Flow> flowList = getSwitchPortFlows(switchId, port);
        if (flowList != null) {
            return toFlowsInfo(flowList, getSwitchNames());
        }
        return null;
    }

    /**
     * Gets the switch flows by ports.
     *
     * @return the FlowInfo
     */
    public SwitchFlowsInfoPerPort getSwitchFlowsByPorts(String switchId, List<Integer> ports) {
        StringBuilder urlBuilder = new StringBuilder(applicationProperties.getNbBaseUrl()
                + IConstants.NorthBoundUrl.GET_SWITCH_FLOWS_BY_PORTS
                .replace("{switch_id}", switchId));

        if (!CollectionUtils.isEmpty(ports)) {
            urlBuilder.append("?");
            for (Integer port : ports) {
                urlBuilder.append("ports").append("=").append(port).append("&");
            }
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }

        SwitchFlowsPerPort switchFlowsPerPort;
        HttpResponse response = restClientManager.invoke(urlBuilder.toString(), HttpMethod.GET,
                "", "", applicationService.getAuthHeader());
        try {
            RestClientManager.isValidResponse(response);
            switchFlowsPerPort = restClientManager.getResponse(response, SwitchFlowsPerPort.class);
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting switch flows", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return toFlowV2InfosPerPorts(switchFlowsPerPort, getSwitchNames());
    }

    /**
     * Gets the switch flows.
     *
     * @return the Flow
     */
    public List<Flow> getSwitchPortFlows(String switchId, String port) {
        try {
            HttpResponse response;
            if (port == null) {
                response = restClientManager.invoke(
                        applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                                .GET_SWITCH_FLOWS.replace("{switch_id}", switchId),
                        HttpMethod.GET, "", "", applicationService.getAuthHeader());
            } else {
                response = restClientManager.invoke(
                        applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                                .GET_SWITCH_PORT_FLOWS.replace("{switch_id}", switchId).replace("{port}", port),
                        HttpMethod.GET, "", "", applicationService.getAuthHeader());
            }
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, Flow.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting switch flows", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Deletes the switch.
     *
     * @return the switch info
     */
    public SwitchInfo deleteSwitch(String switchId, boolean force) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                            .DELETE_SWITCH.replace("{switch_id}", switchId).replace("{force}", String.valueOf(force)),
                    HttpMethod.DELETE, "", "",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchInfo.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while deleting switch:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Gets the isl links.
     *
     * @return the IslLinkInfo
     */
    public List<IslLinkInfo> updateIslBfdFlag(final LinkParametersDto linkParametersDto) {
        List<IslLink> links = updateLinkBfdFlag(linkParametersDto);
        if (CollectionUtil.isEmpty(links)) {
            throw new ContentNotFoundException();
        }
        return toIslLinksInfo(links, islCostMap(null), getSwitchNames());
    }

    /**
     * Updates the isl bfd flag.
     *
     * @return the IslLink
     */
    public List<IslLink> updateLinkBfdFlag(final LinkParametersDto linkParametersDto) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.UPDATE_LINK_BFD_FLAG,
                    HttpMethod.PATCH, objectMapper.writeValueAsString(linkParametersDto),
                    "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, IslLink.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while updating isl bfd-flag", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while updating isl bfd-flag", e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Updates the switch port property.
     *
     * @return the SwitchProperty
     */
    public SwitchProperty updateSwitchPortProperty(String switchId, String port, SwitchProperty switchProperty) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.UPDATE_SWITCH_PORT_PROPERTY
                            .replace("{switch_id}", switchId).replace("{port}", String.valueOf(port)),
                    HttpMethod.PUT, objectMapper.writeValueAsString(switchProperty),
                    "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchProperty.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while updating switch port property", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.error("Error occurred while updating switch port property", e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Gets the switch port property.
     *
     * @return the SwitchProperty
     */
    public SwitchProperty getSwitchPortProperty(String switchId, String port) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_SWITCH_PORT_PROPERTY
                            .replace("{switch_id}", switchId).replace("{port}", String.valueOf(port)),
                    HttpMethod.GET, "", "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchProperty.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting switch port property", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Updates the switch location.
     *
     * @return the SwitchInfo
     */
    public SwitchInfo updateSwitchLocation(String switchId, SwitchLocation switchLocation) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                            .UPDATE_SWITCH_LOCATION.replace("{switch_id}", switchId),
                    HttpMethod.PATCH, objectMapper.writeValueAsString(switchLocation), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchInfo.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while updating switch location:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.warn("Error occurred while updating switch location:" + switchId, e);
            throw new IntegrationException(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Gets the link Bfd properties.
     *
     * @param srcSwitch the src switch
     * @param srcPort   the src port
     * @param dstSwitch the dst switch
     * @param dstPort   the dst port
     * @return the link Bfd properties
     */
    public LinkBfdProperties getLinkBfdProperties(String srcSwitch, String srcPort, String dstSwitch, String dstPort) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_BFD_PROPERTIES
                            .replace("{src-switch}", srcSwitch).replace("{src-port}", srcPort)
                            .replace("{dst-switch}", dstSwitch).replace("{dst-port}", dstPort), HttpMethod.GET,
                    "", "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, LinkBfdProperties.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while reading link bfd properties", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Updates the link Bfd properties.
     *
     * @return the LinkBfdProperties
     */
    public LinkBfdProperties updateLinkBfdProperties(String srcSwitch, String srcPort, String dstSwitch,
                                                     String dstPort, BfdProperties properties) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_BFD_PROPERTIES
                            .replace("{src-switch}", srcSwitch).replace("{src-port}", srcPort)
                            .replace("{dst-switch}", dstSwitch).replace("{dst-port}", dstPort), HttpMethod.PUT,
                    objectMapper.writeValueAsString(properties), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, LinkBfdProperties.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while updating link bfd properties", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Deletes link bfd.
     *
     * @param srcSwitch the src switch
     * @param srcPort   the src port
     * @param dstSwitch the dst switch
     * @param dstPort   the dst port
     * @return LinkBfdProperties
     */
    public String deleteLinkBfd(String srcSwitch, String srcPort, String dstSwitch, String dstPort) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl.GET_LINK_BFD_PROPERTIES
                            .replace("{src-switch}", srcSwitch).replace("{src-port}", srcPort)
                            .replace("{dst-switch}", dstSwitch).replace("{dst-port}", dstPort), HttpMethod.DELETE,
                    "", "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isDeleteBfdValidResponse(response)) {
                return restClientManager.getResponse(response, String.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occured while deleting link bfd", e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (UnsupportedOperationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Creates switch logical port.
     *
     * @param switchId          the switch id
     * @param switchLogicalPort the switch logical port
     * @return the SwitchLogicalPort
     */
    public SwitchLogicalPort createLogicalPort(String switchId, SwitchLogicalPort switchLogicalPort) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                            .SWITCH_LOGICAL_PORT.replace("{switch_id}", switchId),
                    HttpMethod.POST, objectMapper.writeValueAsString(switchLogicalPort), "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchLogicalPort.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while creating switch logical port:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        } catch (JsonProcessingException e) {
            LOGGER.warn("Error occurred while creating switch logical port:" + switchId, e);
            throw new IntegrationException(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Deletes switch logical port.
     *
     * @param switchId          the switch id
     * @param logicalPortNumber the switch logical port number
     * @return the SwitchLogicalPort
     */
    public SwitchLogicalPort deleteLogicalPort(String switchId, String logicalPortNumber) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                            .DELETE_SWITCH_LOGICAL_PORT.replace("{switch_id}", switchId)
                            .replace("{logical_port_number}", logicalPortNumber),
                    HttpMethod.DELETE, "", "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, SwitchLogicalPort.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while deleting switch logical port:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }

    /**
     * Gets switch logical ports.
     *
     * @param switchId the switch id
     * @return the SwitchLogicalPort
     */
    public List<SwitchLogicalPort> getLogicalPort(String switchId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getNbBaseUrl() + IConstants.NorthBoundUrl
                            .SWITCH_LOGICAL_PORT.replace("{switch_id}", switchId),
                    HttpMethod.GET, "", "application/json",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, SwitchLogicalPort.class);
            }
        } catch (InvalidResponseException e) {
            LOGGER.error("Error occurred while getting switch logical port:" + switchId, e);
            throw new InvalidResponseException(e.getCode(), e.getResponse());
        }
        return null;
    }
}
