package org.openkilda.integration.service;

import java.util.List;

import org.apache.http.HttpResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

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


    /**
     * Gets the flows.
     *
     * @return the flows
     * @throws IntegrationException
     */
    public List<FlowInfo> getFlows() {

        List<Flow> flowList = getAllFlowList();
        if (flowList != null) {
            return flowConverter.toFlowsInfo(flowList);
        }
        return null;
    }


    /**
     * Gets the flow status.
     *
     * @param flowId the flow id
     * @return the flow status
     * @throws IntegrationException
     */
    public String getFlowStatus(final String flowId) throws IntegrationException {
        FlowStatus flowStatus = null;
        HttpResponse response =
                restClientManager.invoke(applicationProperties.getFlowStatus() + flowId,
                        HttpMethod.GET, "", "", applicationService.getAuthHeader());
        if (RestClientManager.isValidResponse(response)) {
            flowStatus = restClientManager.getResponse(response, FlowStatus.class);
            return flowStatus.getStatus();
        }
        return null;
    }


    /**
     * Gets the flow paths.
     *
     * @return the flow paths
     * @throws IntegrationException
     */
    public FlowPath getFlowPath(final String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getTopologyFlows() + "/" + flowId, HttpMethod.GET, "", "",
                    "");
            if (RestClientManager.isValidResponse(response)) {
                FlowPayload flowPayload =
                        restClientManager.getResponse(response, FlowPayload.class);
                return flowPathConverter.getFlowPath(flowId, flowPayload);
            } else {
                String content = IoUtil.toString(response.getEntity().getContent());
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(),
                        content);
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
     * @throws IntegrationException
     */
    public List<Flow> getAllFlowList() {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getFlows(),
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
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
     * Re route flow by flow id.
     * 
     * @param flowId
     * @return flow path.
     */
    public FlowPath rerouteFlow(String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getFlowReroute().replace("{flow_id}", flowId),
                    HttpMethod.PATCH, "", "", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                FlowPath flowPath = restClientManager.getResponse(response, FlowPath.class);
                return flowPath;
            } else {
                String content = IoUtil.toString(response.getEntity().getContent());
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(),
                        content);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in rerouteFlow " + exception.getMessage());
            throw new IntegrationException(exception);
        }
    }

    /**
     * Flow validation by flow id.
     * 
     * @param flowId
     * @return
     */
    public String validateFlow(String flowId) {
        try {
            HttpResponse response = restClientManager.invoke(
                    applicationProperties.getFlowValidate().replace("{flow_id}", flowId),
                    HttpMethod.GET, "", "", applicationService.getAuthHeader());
            return IoUtil.toString(response.getEntity().getContent());
        } catch (Exception exception) {
            LOGGER.error("Exception in validateFlow " + exception.getMessage());
            throw new IntegrationException(exception);
        }
    }

    /**
     * Flow by flow id.
     * 
     * @param flowId
     * @return
     */
    public Flow getFlowById(String flowId) {
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getFlows() + "/" + flowId,
                            HttpMethod.GET, "", "", applicationService.getAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                return restClientManager.getResponse(response, Flow.class);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowById " + exception.getMessage());
            throw new IntegrationException(exception);
        }
        return null;
    }
}
