package org.bitbucket.openkilda.flow;

import static java.util.Base64.getEncoder;
import static org.bitbucket.openkilda.DefaultParameters.northboundEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyPassword;
import static org.bitbucket.openkilda.DefaultParameters.topologyUsername;

import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;

import java.io.IOException;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


public class FlowUtils {
    private static final String auth = topologyUsername + ":" + topologyPassword;
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());

    /**
     * Gets flow through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow
     */
    public static FlowPayload getFlow(final String flowId) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Northbound Get Flow");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Get Flow Time: %,.3f", getTimeDuration(current)));

        return response.getStatus() == 404 ? null : response.readEntity(FlowPayload.class);
    }

    /**
     * Creates flow through Northbound service.
     *
     * @param payload flow JSON data
     * @return The JSON document of the created flow
     */
    public static FlowPayload putFlow(final FlowPayload payload) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .put(Entity.json(payload));

        System.out.println("\n== Northbound Create Flow");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Create Flow Time: %,.3f", getTimeDuration(current)));

        return response.readEntity(FlowPayload.class);
    }

    /**
     * Updates flow through Northbound service.
     *
     * @param flowId  flow id
     * @param payload flow JSON data
     * @return The JSON document of the created flow
     */
    public static FlowPayload updateFlow(final String flowId, final FlowPayload payload) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .put(Entity.json(payload));

        System.out.println("\n== Northbound Update Flow");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Update Flow Time: %,.3f", getTimeDuration(current)));

        return response.readEntity(FlowPayload.class);
    }

    /**
     * Deletes flow through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow
     */
    public static FlowIdStatusPayload deleteFlow(final String flowId) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .delete();

        System.out.println("\n== Northbound Delete Flow");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Delete Flow Time: %,.3f", getTimeDuration(current)));

        return response.getStatus() == 404 ? null : response.readEntity(FlowIdStatusPayload.class);
    }

    /**
     * Returns flow through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<Flow> dumpFlows() throws IOException {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/flows")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Northbound Dump Flows");
        System.out.println(String.format("==> response = %s", response));
        System.out.println(String.format("==> Northbound Dump Flows Time: %,.3f", getTimeDuration(current)));

        return new ObjectMapper().readValue(response.readEntity(String.class), new TypeReference<List<Flow>>(){});
    }

    /**
     * Cleanups all flows.
     */
    public static void cleanupFlows() {
        try {
            List<Flow> flows = dumpFlows();
            for (Flow flow : flows) {
                deleteFlow(flow.getFlowId());
            }
        } catch (Exception exception) {
            System.out.println(String.format("Error during flow deletion: %s", exception.getMessage()));
            exception.printStackTrace();
        }
    }

    private static double getTimeDuration(final long current) {
        return (System.currentTimeMillis() - current) / 1000.0;
    }
}
