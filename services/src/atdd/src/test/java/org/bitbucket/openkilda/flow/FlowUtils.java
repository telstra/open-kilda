package org.bitbucket.openkilda.flow;

import static java.util.Base64.getEncoder;
import static org.bitbucket.openkilda.DefaultParameters.northboundEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.pathComputer;
import static org.bitbucket.openkilda.DefaultParameters.topologyEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyPassword;
import static org.bitbucket.openkilda.DefaultParameters.topologyUsername;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.HealthCheck;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;
import org.bitbucket.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPathPayload;
import org.bitbucket.openkilda.messaging.payload.flow.FlowPayload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


public class FlowUtils {
    private static final String auth = topologyUsername + ":" + topologyPassword;
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());
    private static final String FEATURE_TIME = String.valueOf(System.currentTimeMillis());

    public static int getHealthCheck() {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/health-check")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Northbound Health-Check");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Health-Check: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            System.out.println(String.format("===> Health-Check = %s", response.readEntity(HealthCheck.class)));
        }

        return responseCode;
    }

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

        return response.getStatus() == 200 ? response.readEntity(FlowPayload.class) : null;
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
    public static FlowPayload deleteFlow(final String flowId) {
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

        return response.getStatus() == 404 ? null : response.readEntity(FlowPayload.class);
    }

    /**
     * Gets flow path through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow path
     */
    public static FlowPathPayload getFlowPath(final String flowId) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/path")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Northbound Get Flow Path");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Get Flow Path Time: %,.3f", getTimeDuration(current)));

        return response.getStatus() == 404 ? null : response.readEntity(FlowPathPayload.class);
    }

    /**
     * Gets flow status through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow status
     */
    public static FlowIdStatusPayload getFlowStatus(final String flowId) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/status")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Northbound Get Flow Status");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Get Flow Status Time: %,.3f", getTimeDuration(current)));

        return response.getStatus() == 404 ? null : response.readEntity(FlowIdStatusPayload.class);
    }

    /**
     * Gets flows dump through Northbound service.
     *
     * @return The JSON document of the dump flows
     */
    public static List<FlowPayload> getFlowDump() {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Northbound Get Flow Dump");
        System.out.println(String.format("==> response = %s", response.toString()));
        System.out.println(String.format("==> Northbound Get Flow Dump Time: %,.3f", getTimeDuration(current)));

        if (response.getStatus() == 404) {
            return Collections.emptyList();
        } else {
            return response.readEntity(new GenericType<List<FlowPayload>>() {
            });
        }
    }

    /**
     * Returns flows through Topology-Engine-Rest service.
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

        System.out.println("\n== Topology-Engine Dump Flows");
        System.out.println(String.format("==> response = %s", response));
        System.out.println(String.format("==> Topology-Engine Dump Flows Time: %,.3f", getTimeDuration(current)));

        return new ObjectMapper().readValue(response.readEntity(String.class), new TypeReference<List<Flow>>() {
        });
    }

    /**
     * Returns links through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<IslInfoData> dumpLinks() throws Exception {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/links")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n== Topology-Engine Dump Links");
        System.out.println(String.format("==> response = %s", response));
        System.out.println(String.format("==> Topology-Engine Dump Links Time: %,.3f", getTimeDuration(current)));

        List<IslInfoData> links = new ObjectMapper().readValue(
                response.readEntity(String.class), new TypeReference<List<IslInfoData>>() {
                });
        System.out.println(String.format("===> Data = %s", links));

        return links;
    }

    /**
     * Returns link available bandwidth through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static Integer getLinkBandwidth(final String src_switch, final String src_port) throws Exception {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/links/bandwidth/")
                .path("{src_switch}")
                .path("{src_port}")
                .resolveTemplate("src_switch", src_switch)
                .resolveTemplate("src_port", src_port)
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println("\n==> Topology-Engine Dump Links");
        System.out.println(String.format("==> Response = %s", response));
        System.out.println(String.format("==> Topology-Engine Dump Links Time: %,.3f", getTimeDuration(current)));

        Integer bandwidth = new ObjectMapper().readValue(response.readEntity(String.class), Integer.class);
        System.out.println(String.format("===> Link switch=%s port=%s bandwidth=%d", src_switch, src_port, bandwidth));

        return bandwidth;
    }

    /**
     * Cleanups all flows.
     */
    public static void cleanupFlows() throws Exception {
        try {
            for (Flow flow : dumpFlows()) {
                deleteFlow(flow.getFlowId());
            }

            for (FlowPayload flow : getFlowDump()) {
                deleteFlow(flow.getId());
            }
        } catch (Exception exception) {
            System.out.println(String.format("Error during flow deletion: %s", exception.getMessage()));
            exception.printStackTrace();
        }
    }

    /**
     * Builds flow name by flow id.
     *
     * @param flowId flow id
     * @return flow name
     */
    public static String getFlowName(final String flowId) {
        return String.format("%s-%s", flowId, FEATURE_TIME);
    }

    /**
     * Returns timestamp difference.
     *
     * @param current current timestamp
     * @return timestamp difference
     */
    public static double getTimeDuration(final long current) {
        return (System.currentTimeMillis() - current) / 1000.0;
    }

    /**
     * Gets flow path.
     *
     * @param flow flow
     * @return flow path
     */
    public static ImmutablePair<PathInfoData, PathInfoData> getFlowPath(Flow flow) throws Exception {
        pathComputer.init();
        Thread.sleep(1000);
        return pathComputer.getPath(flow);
    }
}
