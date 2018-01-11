package org.openkilda.integration.service;

import java.util.List;

import org.apache.http.HttpResponse;
import org.json.simple.JSONObject;
import org.openkilda.helper.RestClientManager;
import org.openkilda.integration.model.response.LinkResponse;
import org.openkilda.integration.model.response.SwitchResponse;
import org.openkilda.utility.ApplicationProperties;
import org.openkilda.utility.IoUtils;
import org.openkilda.utility.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SwitchIntegrationService.
 * 
 * @author Gaurav Chugh
 */
@Service
public class SwitchIntegrationService {

    /** The Constant _log. */
    private static final Logger log = LoggerFactory.getLogger(SwitchIntegrationService.class);

    /** The rest client manager. */
    @Autowired
    RestClientManager restClientManager;

    /** The application properties. */
    @Autowired
    ApplicationProperties applicationProperties;

    /** The util. */
    @Autowired
    private Util util;

    /** The object mapper. */
    @Autowired
    ObjectMapper objectMapper;


    /**
     * Gets the switches.
     *
     * @return the switches
     */
    public List<SwitchResponse> getSwitches() {
        List<SwitchResponse> switchResponseList = null;

        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getSwitches(), HttpMethod.GET,
                            "", "", "");
            if (RestClientManager.isValidResponse(response)) {

                switchResponseList =
                        restClientManager.getResponseList(response, SwitchResponse.class);

            }
        } catch (Exception exception) {
            log.error("Exception in getswitchdataList " + exception.getMessage());
        }
        return switchResponseList;
    }


    /**
     * Gets the isl links.
     *
     * @return the isl links
     */
    public List<LinkResponse> getIslLinks() {
        List<LinkResponse> linkResponseList = null;

        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getLinks(), HttpMethod.GET, "",
                            "", "");
            if (RestClientManager.isValidResponse(response)) {

                linkResponseList = restClientManager.getResponseList(response, LinkResponse.class);
            }
        } catch (Exception exception) {
            log.error("Exception in getAllLinks " + exception.getMessage());
        }
        return linkResponseList;
    }

    /**
     * Gets the switch ports.
     *
     * @return the switch ports
     */
    public JSONObject getSwitchPorts() {
        JSONObject jsonObject = null;
        try {
            HttpResponse response =
                    restClientManager.invoke(applicationProperties.getSwitchPorts(),
                            HttpMethod.GET, "", "", "");
            if (RestClientManager.isValidResponse(response)) {
                String responseEntity = IoUtils.getData(response.getEntity().getContent());
                jsonObject = objectMapper.readValue(responseEntity, JSONObject.class);
            }
        } catch (Exception exception) {
            log.error("Exception in getSwitchPorts " + exception.getMessage());
        }
        return jsonObject;
    }

}
