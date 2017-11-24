package org.openkilda.helper;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openkilda.model.FlowPath;
import org.openkilda.switchhelper.ProcessSwitchDetails;
import org.openkilda.ws.response.FlowStatusResponse;
import org.openkilda.ws.response.LinkResponse;
import org.openkilda.ws.response.PortResponse;
import org.openkilda.ws.response.SwitchResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class APIHelper.
 * 
 * @author Gaurav Chugh
 */
@Component
public class APIHelper {

    /** The Constant log. */
    private static final Logger log = Logger.getLogger(ProcessSwitchDetails.class);

    /**
     * Gets the switch list.
     *
     * @param apiURL the api url
     * @return the switch list
     * @throws Exception the exception
     */
    public List<SwitchResponse> getSwitchList(String apiURL) throws Exception {

        log.info("inside APIHelper method getSwitchList");
        RestTemplate restTemplate = new RestTemplate();
        List<SwitchResponse> switchdetails = null;
        String requestURL;
        ResponseEntity<String> response = null;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestURL = apiURL;
            HttpEntity<String> request = new HttpEntity<String>("");
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                protected boolean hasError(HttpStatus statusCode) {
                    return false;
                }
            });
            response = restTemplate.exchange(requestURL, HttpMethod.GET, request, String.class);

            if (response != null && response.getBody() != null
                    && !response.getBody().contains("error-code")) {
                switchdetails = mapper.readValue(response.getBody(), ArrayList.class);
            }

        } catch (Exception ex) {
            throw ex;
        }

        return switchdetails;
    }

    /**
     * Gets the link list.
     *
     * @param apiURL the api url
     * @return the link list
     * @throws Exception the exception
     */
    public List<LinkResponse> getLinkList(String apiURL) throws Exception {

        log.info("inside APIHelper method getLinkList");
        RestTemplate restTemplate = new RestTemplate();
        List<LinkResponse> linkResponse = null;
        String requestURL;
        ResponseEntity<String> response = null;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestURL = apiURL;
            HttpEntity<String> request = new HttpEntity<String>("");
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                protected boolean hasError(HttpStatus statusCode) {
                    return false;
                }
            });
            response = restTemplate.exchange(requestURL, HttpMethod.GET, request, String.class);

            if (response != null && response.getBody() != null
                    && !response.getBody().contains("error-code")) {
                linkResponse = mapper.readValue(response.getBody(), ArrayList.class);
            }

        } catch (Exception ex) {
            throw ex;
        }

        return linkResponse;
    }

    /**
     * Gets the port response.
     *
     * @param apiURL the api url
     * @return the port response
     * @throws Exception the exception
     */
    public PortResponse getPortResponse(String apiURL) throws Exception {

        log.info("inside APIHelper method getPortResponse");
        RestTemplate restTemplate = new RestTemplate();
        PortResponse portResponse = null;
        String requestURL;
        ResponseEntity<String> response = null;
        ObjectMapper mapper = new ObjectMapper();

        try {
            requestURL = apiURL;
            HttpEntity<String> request = new HttpEntity<String>("");
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                protected boolean hasError(HttpStatus statusCode) {
                    return false;
                }
            });
            response = restTemplate.exchange(requestURL, HttpMethod.GET, request, String.class);

            if (response != null && response.getBody() != null
                    && !response.getBody().contains("error-code")) {
                portResponse = mapper.readValue(response.getBody(), PortResponse.class);
            }

        } catch (Exception ex) {
            throw ex;
        }

        return portResponse;
    }

    /**
     * Gets the port json response.
     *
     * @param apiURL the api url
     * @return the port json response
     * @throws Exception the exception
     */
    public JSONObject getPortJsonResponse(String apiURL) throws Exception {
        log.info("inside APIHelper method getPortJsonResponse");

        RestTemplate restTemplate = new RestTemplate();
        String obj = null;
        String requestURL;
        ResponseEntity<String> response = null;
        JSONObject jsonObject = null;

        try {
            requestURL = apiURL;
            HttpEntity<String> request = new HttpEntity<String>("");
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                protected boolean hasError(HttpStatus statusCode) {
                    return false;
                }
            });
            response = restTemplate.exchange(requestURL, HttpMethod.GET, request, String.class);

            if (response != null && response.getBody() != null
                    && !response.getBody().contains("error-code")) {
                obj = response.getBody();
                JSONParser parser = new JSONParser();
                Object obj1 = parser.parse(obj);
                jsonObject = (JSONObject) obj1;

            }

        } catch (Exception ex) {
            throw ex;
        }

        return jsonObject;
    }

    /**
     * Gets the flow list.
     *
     * @param apiURL the api url
     * @return the flow list
     * @throws Exception the exception
     */
    public List<FlowPath> getFlowList(String apiURL) throws Exception {

        log.info("inside APIHelper method getFlowList");
        RestTemplate restTemplate = new RestTemplate();
        List<FlowPath> flowList = new ArrayList<FlowPath>();
        String requestURL;
        ResponseEntity<String> response = null;
        ObjectMapper mapper = new ObjectMapper();
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
        headers.add("Authorization", "Basic a2lsZGE6a2lsZGE=");

        try {
            requestURL = apiURL;
            HttpEntity<String> request = new HttpEntity<String>("", headers);
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                protected boolean hasError(HttpStatus statusCode) {
                    return false;
                }
            });
            response = restTemplate.exchange(requestURL, HttpMethod.GET, request, String.class);

            if (response != null && response.getBody() != null
                    && !response.getBody().contains("error-code")) {
                flowList = mapper.readValue(response.getBody(), ArrayList.class);
            }

        } catch (Exception ex) {
            throw ex;
        }

        return flowList;
    }

    /**
     * Gets the flow status.
     *
     * @param apiURL the api url
     * @param switchId the switch id
     * @return the flow status
     * @throws Exception the exception
     */
    public static String getFlowStatus(String apiURL, String switchId) throws Exception {

        log.info("inside APIHelper method getFlowStatus");
        RestTemplate restTemplate = new RestTemplate();
        FlowStatusResponse flowStatusResponse = new FlowStatusResponse();
        String requestURL;
        ResponseEntity<String> response = null;
        ObjectMapper mapper = new ObjectMapper();
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
        headers.add("Authorization", "Basic a2lsZGE6a2lsZGE=");

        try {
            requestURL = apiURL + switchId;
            HttpEntity<String> request = new HttpEntity<String>("", headers);
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                protected boolean hasError(HttpStatus statusCode) {
                    return false;
                }
            });
            response = restTemplate.exchange(requestURL, HttpMethod.GET, request, String.class);

            if (response != null && response.getBody() != null
                    && !response.getBody().contains("error-code")) {
                flowStatusResponse = mapper.readValue(response.getBody(), FlowStatusResponse.class);
            }

        } catch (Exception ex) {
            throw ex;
        }

        return flowStatusResponse.getStatus();
    }

}
