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

import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.converter.FlowConverter;
import org.openkilda.integration.converter.FlowPathConverter;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.service.ApplicationService;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The Class FlowsIntegrationService.
 *
 * @author Gaurav Chugh
 */

@Service
public class FlowsIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsIntegrationService.class);

    @Autowired
    private RestClientManager restClientManager;

    @Autowired
    FlowPathConverter flowPathConverter;

    @Autowired
    FlowConverter flowConverter;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Gets the flows.
     *
     * @return the flows
     */
    public List<FlowInfo> getFlows() {

        List<Flow> flowList = getAllFlowList();
        if (flowList != null) {
            return flowConverter.toFlowsInfo(flowList);
        }
        return null;
    }

    /**
     * Gets the flow status by id.
     *
     * @param flowId the flow id
     * @return the flow status by id
     * @throws IntegrationException the integration exception
     */
    public FlowStatus getFlowStatusById(final String flowId) throws IntegrationException {
        HttpResponse response = restClientManager.invoke(applicationProperties.getFlowStatus() + flowId, HttpMethod.GET,
                "", "", applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            return restClientManager.getResponse(response, FlowStatus.class);
        }
        return null;
    }

    /**
     * Gets the flow path.
     *
     * @param flowId the flow id
     * @return the flow path
     */
    public FlowPayload getFlowPath(final String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getFlowPath().replace("{flow_id}", flowId), HttpMethod.GET, "", "",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                FlowPayload flowPayload = restClientManager.getResponse(response, FlowPayload.class);
                return flowPathConverter.getFlowPath(flowId, flowPayload);
            } else {
                String content = IoUtil.toString(response.getEntity().getContent());
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(), content);
            }

        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowPaths " + exception.getMessage());
            throw new IntegrationException(exception);
        }
    }

    /**
     * Gets the all flow list.
     *
     * @return the all flow list
     */
    public List<Flow> getAllFlowList() {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getFlows(), HttpMethod.GET, "", "",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponseList(response, Flow.class);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getAllFlowList " + exception.getMessage());
            throw new IntegrationException(exception);
        }
        return null;
    }

    /**
     * Reroute flow.
     *
     * @param flowId the flow id
     * @return the flow path
     */
    public FlowPath rerouteFlow(String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getFlowReroute().replace("{flow_id}", flowId), HttpMethod.PATCH, "", "",
                    applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                FlowPath flowPath = restClientManager.getResponse(response, FlowPath.class);
                return flowPath;
            } else {
                String content = IoUtil.toString(response.getEntity().getContent());
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(), content);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in rerouteFlow " + exception.getMessage());
            throw new IntegrationException(exception);
        }
    }

    /**
     * Validate flow.
     *
     * @param flowId the flow id
     * @return the string
     */
    public String validateFlow(String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getFlowValidate().replace("{flow_id}", flowId), HttpMethod.GET, "", "",
                    applicationService.getAuthHeader());
            return IoUtil.toString(response.getEntity().getContent());
        } catch (Exception exception) {
            LOGGER.error("Exception in validateFlow " + exception.getMessage());
            throw new IntegrationException(exception);
        }
    }

    /**
     * Gets the flow by id.
     *
     * @param flowId the flow id
     * @return the flow by id
     */
    public Flow getFlowById(String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getFlows() + "/" + flowId,
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                Flow flow = restClientManager.getResponse(response, Flow.class);
                return flowConverter.toFlowWithSwitchNames(flow);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowById " + exception.getMessage());
            throw new IntegrationException(exception);
        }
        return null;
    }

    /**
     * Creates the flow.
     *
     * @param flow the flow
     * @return the flow
     */
    public Flow createFlow(Flow flow) {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getFlows(), HttpMethod.PUT,
                    objectMapper.writeValueAsString(flow), "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, Flow.class);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Inside createFlow  Exception :", e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Update flow.
     *
     * @param flowId the flow id
     * @param flow the flow
     * @return the flow
     */
    public Flow updateFlow(String flowId, Flow flow) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getUpdateFlow().replace("{flow_id}", flowId), HttpMethod.PUT,
                    objectMapper.writeValueAsString(flow), "application/json", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, Flow.class);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Inside updateFlow  Exception :", e);
            throw new IntegrationException(e);
        }
        return null;
    }

    /**
     * Delete flow.
     *
     * @param flowId the flow id
     * @return the flow
     */
    public Flow deleteFlow(String flowId) {
        HttpResponse response = restClientManager.invoke(
                applicationProperties.getUpdateFlow().replace("{flow_id}", flowId), HttpMethod.DELETE, "",
                "application/json", applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            return restClientManager.getResponse(response, Flow.class);
        }
        return null;
    }
}
