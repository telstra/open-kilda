package org.openkilda.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

import org.apache.http.HttpResponse;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.converter.FlowConverter;
import org.openkilda.integration.model.response.Flow;
import org.openkilda.integration.model.response.FlowStatus;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.model.FlowInfo;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.Util;

/**
 * The Class FlowsIntegrationService.
 *
 * @author Gaurav Chugh
 */
@Service
public class FlowsIntegrationService {

    /** The Constant _log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsIntegrationService.class);

    /** The rest client manager. */
    @Autowired
    RestClientManager restClientManager;

    /** The application properties. */
    @Autowired
    ApplicationProperties applicationProperties;

    /** The util. */
    @Autowired
    private Util util;


    /**
     * Gets the flows.
     *
     * @return the flows
     */
    public List<FlowInfo> getFlows() {
        try {
            HttpResponse response = restClientManager.invoke(applicationProperties.getFlows(),
                    HttpMethod.GET, "", "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {

                List<Flow> flowList = restClientManager.getResponseList(response, Flow.class);
                return FlowConverter.toFlowsInfo(flowList);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlows " + exception.getMessage());
        }
        return null;
    }


    /**
     * Gets the flow status.
     *
     * @param flowId the flow id
     * @return the flow status
     */
    public FlowStatus getFlowStatus(final String flowId) {
        FlowStatus flowStatus = null;
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getFlowStatus() + flowId,
                            HttpMethod.GET, "", "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {
                flowStatus = restClientManager.getResponse(response, FlowStatus.class);
            }
        } catch (Exception exception) {
            LOGGER.error("Exception in getAllFlows " + exception.getMessage());
        }
        return flowStatus;
    }


    /**
     * Gets the flow paths.
     *
     * @return the flow paths
     */
    public PathLinkResponse[] getFlowPaths() {
        PathLinkResponse[] pathLinkResponse = null;

        try {
            HttpResponse linkResponse = restClientManager
                    .invoke(applicationProperties.getTopologyFlows(), HttpMethod.GET, "", "", "");
            if (RestClientManager.isValidResponse(linkResponse)) {

                pathLinkResponse =
                        restClientManager.getResponse(linkResponse, PathLinkResponse[].class);
            }

        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowPaths " + exception.getMessage());
        }
        return pathLinkResponse;
    }
}
