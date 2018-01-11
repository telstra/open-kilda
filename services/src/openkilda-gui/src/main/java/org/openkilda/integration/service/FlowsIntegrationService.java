package org.openkilda.integration.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.model.response.FlowPath;
import org.openkilda.integration.model.response.FlowStatusResponse;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.integration.model.response.TopologyFlowsResponse;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.Util;
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

    /** The Constant _log. */
    private static final Logger log = LoggerFactory.getLogger(FlowsIntegrationService.class);

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
    public List<FlowPath> getFlows() {
        List<FlowPath> flowPathList = new ArrayList<FlowPath>();
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getFlows(), HttpMethod.GET, "",
                            "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {

                flowPathList = restClientManager.getResponseList(response, FlowPath.class);
            }

        } catch (Exception exception) {
            log.error("Exception in getAllFlows " + exception.getMessage());
        }
        return flowPathList;
    }


    /**
     * Gets the flow status.
     *
     * @param flowId the flow id
     * @return the flow status
     */
    public FlowStatusResponse getFlowStatus(String flowId) {
        FlowStatusResponse flowStatusResponse = null;
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getFlowStatus() + flowId,
                            HttpMethod.GET, "", "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {

                flowStatusResponse =
                        restClientManager.getResponse(response, FlowStatusResponse.class);

            }
        } catch (Exception exception) {
            log.error("Exception in getAllFlows " + exception.getMessage());
        }
        return flowStatusResponse;
    }


    /**
     * Gets the topology flows.
     *
     * @return the topology flows
     */
    public List<TopologyFlowsResponse> getTopologyFlows() {
        List<TopologyFlowsResponse> flowPathList = new ArrayList<TopologyFlowsResponse>();
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getTopologyFlows(),
                            HttpMethod.GET, "", "", util.kildaAuthHeader());
            if (RestClientManager.isValidResponse(response)) {

                flowPathList =
                        restClientManager.getResponseList(response, TopologyFlowsResponse.class);
            }

        } catch (Exception exception) {
            log.error("Exception in getTopologyFlows " + exception.getMessage());
        }
        return flowPathList;
    }


    /**
     * Gets the flow paths.
     *
     * @return the flow paths
     */
    public PathLinkResponse[] getFlowPaths() {
        PathLinkResponse[] pathLinkResponse = null;

        try {
            HttpResponse linkResponse =
                    restClientManager.invoke(applicationProperties.getTopologyFlows(), HttpMethod.GET,
                            "", "", "");
            if (RestClientManager.isValidResponse(linkResponse)) {

                pathLinkResponse =
                        restClientManager.getResponse(linkResponse, PathLinkResponse[].class);
            }

        } catch (Exception exception) {
            log.error("Exception in getFlowPaths " + exception.getMessage());
        }
        return pathLinkResponse;
    }
}
