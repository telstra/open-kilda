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
import org.openkilda.integration.converter.FlowPathConverter;
import org.openkilda.integration.exception.ContentNotFoundException;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.model.Flow;
import org.openkilda.integration.model.FlowStatus;
import org.openkilda.integration.model.response.PathLinkResponse;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.service.ApplicationService;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.CollectionUtil;
import org.openkilda.utility.IoUtil;

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
    private ApplicationProperties applicationProperties;

    @Autowired
    private ApplicationService applicationService;


    /**
     * Gets the flows.
     *
     * @return the flows
     * @throws IntegrationException
     */
    public List<FlowInfo> getFlows()  {
        
            List<Flow> flowList = getAllFlowList();
	            if(flowList != null) {
		            List<FlowInfo> flows = FlowConverter.toFlowsInfo(flowList);
		            if (!CollectionUtil.isEmpty(flows)) {
		                flows.forEach(flowInfo -> {
		                    try {
		                        String status = getFlowStatus(flowInfo.getFlowid());
		                        flowInfo.setStatus(status);
		                    } catch (Exception e) {
		                        LOGGER.error("Exception while retriving flow status. Exception: " + e, e);
		                    }
		                });
		            } else {
		                    throw new ContentNotFoundException();
		            }
		            return flows;
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
            HttpResponse response = restClientManager
                    .invoke(applicationProperties.getTopologyFlows(), HttpMethod.GET, "", "", "");
            if (RestClientManager.isValidResponse(response)) {
                PathLinkResponse[] pathLinkResponse =
                        restClientManager.getResponse(response, PathLinkResponse[].class);
                return FlowPathConverter.getFlowPath(flowId, pathLinkResponse);
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
		}catch (Exception exception) {
            LOGGER.error("Exception in getAllFlowList " + exception.getMessage());
            throw new IntegrationException(exception);
        }
        return null;
	}
}
