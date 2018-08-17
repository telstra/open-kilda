package org.openkilda.atdd.utils.controller;

import static org.awaitility.Awaitility.await;

import org.openkilda.DefaultParameters;
import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ControllerUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerUtils.class);
    private final Client restClient;

    public ControllerUtils() throws DockerCertificateException, DockerException, InterruptedException {
        restClient = ClientBuilder.newClient(new ClientConfig());
    }

    public void restart() throws DockerException, InterruptedException {
        await().atMost(10, TimeUnit.SECONDS)
                .until(this::isAlive);
    }

    public void addStaticFlow(StaticFlowEntry flow) throws JsonProcessingException, FloodlightQueryException {
        Response response;
        String json = Utils.MAPPER.writeValueAsString(flow);
        try {
            response = restClient.target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                    .path("/wm/staticentrypusher/json")
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.json(json));
        } catch (ProcessingException e) {
            throw new FloodlightQueryException(e);
        }

        if (response.getStatus() != 200) {
            throw new FloodlightQueryException(201, response.getStatus());
        }
    }

    public DpIdEntriesList listStaticEntries(String dpId) throws FloodlightQueryException {
        Response response;
        try {
            response = restClient.target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                    .path(String.format("/wm/staticentrypusher/list/%s/json", dpId))
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .get();
        } catch (ProcessingException e) {
            throw new FloodlightQueryException(e);
        }

        if (response.getStatus() != 200) {
            throw new FloodlightQueryException(200, response.getStatus());
        }

        String json = response.readEntity(String.class);
        try {
            return Utils.MAPPER.readValue(json, DpIdEntriesList.class);
        } catch (IOException e) {
            throw new FloodlightQueryException("Can't parse FloodLight response", e);
        }
    }

    public DpIdEntriesList listStaticEntries() throws FloodlightQueryException {
        return this.listStaticEntries("all");
    }

    public List<CoreFlowEntry> listCoreFlows(String dpId) throws FloodlightQueryException, DpIdNotFoundException {
        Response response;
        try {
            response = restClient.target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                    .path(String.format("/wm/core/switch/%s/flow/json", dpId))
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .get();
        } catch (ProcessingException e) {
            throw new FloodlightQueryException(e);
        }

        if (response.getStatus() != 200) {
            throw new FloodlightQueryException(200, response.getStatus());
        }

        final String flowsKey = "flows";
        String json = response.readEntity(String.class);
        LOGGER.debug("FloodLight switch \"{}\" list flows response: {}", dpId, json);
        try {
            Map<String, Object> keyChecker = Utils.MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            if (!keyChecker.containsKey(flowsKey)) {
                throw new DpIdNotFoundException();
            }

            Map<String, List<CoreFlowEntry>> wrapper = Utils.MAPPER.readValue(
                    json, new TypeReference<Map<String, List<CoreFlowEntry>>>() {
                    });
            return wrapper.get(flowsKey);
        } catch (IOException e) {
            throw new FloodlightQueryException("Can't parse FloodLight response", e);
        }
    }

    public boolean isAlive() {
        Response response;
        try {
            response = restClient.target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                    .path("/wm/core/controller/summary/json")
                    .request()
                    .get();
        } catch (ProcessingException e) {
            LOGGER.info("floodlight is unavailable");
            return false;
        }

        boolean alive = response != null;
        if (alive) {
            LOGGER.info("floodlight available");
        }
        return alive;
    }

    /**
     * Returns switch port informations.
     */
    public SwitchEntry getSwitchPorts(String switchId) throws FloodlightQueryException {
        Response response;
        try {
            response = restClient.target(DefaultParameters.FLOODLIGHT_ENDPOINT)
                    .path(String.format("wm/core/switch/%s/port-desc/json", switchId))
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .get();
        } catch (ProcessingException e) {
            throw new FloodlightQueryException(e);
        }

        if (response.getStatus() != 200) {
            throw new FloodlightQueryException(200, response.getStatus());
        }

        String json = response.readEntity(String.class);
        try {
            return Utils.MAPPER.readValue(json, SwitchEntry.class);
        } catch (IOException e) {
            throw new FloodlightQueryException("Can't parse FloodLight response", e);
        }
    }
}
