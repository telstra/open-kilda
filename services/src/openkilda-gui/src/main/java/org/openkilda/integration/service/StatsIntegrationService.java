package org.openkilda.integration.service;

import org.apache.http.HttpResponse;
import org.openkilda.constants.HttpError;
import org.openkilda.exception.NoDataFoundException;
import org.openkilda.helper.RestClientManager;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * The Class StatsIntegrationService.
 * 
 * @author Gaurav Chugh
 */
@Service
public class StatsIntegrationService {

    /** The Constant _log. */
    private static final Logger log = LoggerFactory.getLogger(StatsIntegrationService.class);

    /** The rest client manager. */
    @Autowired
    RestClientManager restClientManager;

    /** The application properties. */
    @Autowired
    ApplicationProperties applicationProperties;

    /**
     * Gets the stats.
     *
     * @param requestBody the request body
     * @return the stats
     */
    public String getStats(String requestBody) {
        String responseEntity = "";
        try {
            log.info("Inside getStats:  requestBody: " + requestBody);
            HttpResponse httpResponse =
                    restClientManager.invoke(applicationProperties.getOpenTsdbQuery(),
                            HttpMethod.POST, requestBody, "", "");
            if (RestClientManager.isValidResponse(httpResponse)) {
                responseEntity = IoUtils.toString(httpResponse.getEntity().getContent());
            }
        } catch (Exception ex) {
            log.error("Inside getStats: Exception: " + ex.getMessage());
            throw new NoDataFoundException(HttpError.RESPONSE_NOT_FOUND.getMessage());
        }

        return responseEntity;

    }

}
