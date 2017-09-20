package org.bitbucket.openkilda.flow;

import static java.util.Base64.getEncoder;
import static org.bitbucket.openkilda.DefaultParameters.northboundEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.pathComputer;
import static org.bitbucket.openkilda.DefaultParameters.topologyEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyPassword;
import static org.bitbucket.openkilda.DefaultParameters.topologyUsername;
import static org.junit.Assert.assertEquals;

import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.messaging.error.MessageError;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
        System.out.println("\n==> Northbound Health-Check");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/health-check")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();


        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Health-Check Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            System.out.println(String.format("====> Health-Check = %s",
                    response.readEntity(HealthCheck.class)));
        } else {
            System.out.println(String.format("====> Error: Health-Check = %s",
                    response.readEntity(MessageError.class)));
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
        System.out.println("\n==> Northbound Get Flow");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Get Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(String.format("====> Northbound Get Flow = %s", flow));
            return flow;
        } else {
            System.out.println(String.format("====> Error: Northbound Get Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Creates flow through Northbound service.
     *
     * @param payload flow JSON data
     * @return The JSON document of the created flow
     */
    public static FlowPayload putFlow(final FlowPayload payload) {
        System.out.println("\n==> Northbound Create Flow");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .put(Entity.json(payload));

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Create Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(String.format("====> Northbound Create Flow = %s", flow));
            return flow;
        } else {
            System.out.println(String.format("====> Error: Northbound Create Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Updates flow through Northbound service.
     *
     * @param flowId  flow id
     * @param payload flow JSON data
     * @return The JSON document of the created flow
     */
    public static FlowPayload updateFlow(final String flowId, final FlowPayload payload) {
        System.out.println("\n==> Northbound Update Flow");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .put(Entity.json(payload));

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Update Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(String.format("====> Northbound Update Flow = %s", flow));
            return flow;
        } else {
            System.out.println(String.format("====> Error: Northbound Update Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Deletes flow through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow
     */
    public static FlowPayload deleteFlow(final String flowId) {
        System.out.println("\n==> Northbound Delete Flow");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .delete();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Delete Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(String.format("====> Northbound Delete Flow = %s", flow));
            return flow;
        } else {
            System.out.println(String.format("====> Error: Northbound Delete Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Gets flow path through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow path
     */
    public static FlowPathPayload getFlowPath(final String flowId) {
        System.out.println("\n==> Northbound Get Flow Path");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/path")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();


        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Get Flow Path Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPathPayload flowPath = response.readEntity(FlowPathPayload.class);
            System.out.println(String.format("====> Northbound Get Flow Path = %s", flowPath));
            return flowPath;
        } else {
            System.out.println(String.format("====> Error: Northbound Get Flow Path = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Gets flow status through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow status
     */
    public static FlowIdStatusPayload getFlowStatus(final String flowId) {
        System.out.println("\n==> Northbound Get Flow Status");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/status")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Get Flow Status Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowIdStatusPayload flowStatus = response.readEntity(FlowIdStatusPayload.class);
            System.out.println(String.format("====> Northbound Get Flow Status = %s", flowStatus));
            return flowStatus;
        } else {
            System.out.println(String.format("====> Error: Northbound Get Flow Status = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Gets flows dump through Northbound service.
     *
     * @return The JSON document of the dump flows
     */
    public static List<FlowPayload> getFlowDump() {
        System.out.println("\n==> Northbound Get Flow Dump");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Northbound Get Flow Dump Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            List<FlowPayload> flows = response.readEntity(new GenericType<List<FlowPayload>>() {});
            System.out.println(String.format("====> Northbound Get Flow Dump = %d", flows.size()));
            return flows;
        } else {
            System.out.println(String.format("====> Error: Northbound Get Flow Dump = %s",
                    response.readEntity(MessageError.class)));
            return Collections.emptyList();
        }
    }

    /**
     * Returns flows through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<Flow> dumpFlows() throws IOException {
        System.out.println("\n==> Topology-Engine Dump Flows");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/flows")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Topology-Engine Dump Flows Time: %,.3f", getTimeDuration(current)));

        List<Flow> flows = new ObjectMapper().readValue(response.readEntity(String.class),
                new TypeReference<List<Flow>>() {});
        System.out.println(String.format("====> Topology-Engine Dump Flows = %d", flows.size()));

        return flows;
    }

    /**
     * Returns links through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<IslInfoData> dumpLinks() throws Exception {
        System.out.println("\n==> Topology-Engine Dump Links");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/links")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Topology-Engine Dump Links Time: %,.3f", getTimeDuration(current)));

        List<IslInfoData> links = new ObjectMapper().readValue(
                response.readEntity(String.class), new TypeReference<List<IslInfoData>>() {});
        System.out.println(String.format("====> Data = %s", links));

        return links;
    }

    /**
     * Returns link available bandwidth through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static Integer getLinkBandwidth(final String src_switch, final String src_port) throws Exception {
        System.out.println("\n==> Topology-Engine Link Bandwidth");

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


        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Topology-Engine Link Bandwidth Time: %,.3f", getTimeDuration(current)));

        Integer bandwidth = new ObjectMapper().readValue(response.readEntity(String.class), Integer.class);
        System.out.println(String.format("====> Link switch=%s port=%s bandwidth=%d", src_switch, src_port, bandwidth));

        return bandwidth;
    }

    public static void restoreFlows() throws Exception {
        System.out.println("\n==> Topology-Engine Restore Flows");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/flows/restore")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Topology-Engine Restore Flows Time: %,.3f", getTimeDuration(current)));
    }

    /**
     * Cleanups all flows.
     */
    public static void cleanupFlows() throws Exception {
        try {
            Set<String> flows = new HashSet<>();

            List<Flow> tpeFlows = dumpFlows();
            tpeFlows.forEach(flow->flows.add(flow.getFlowId()));

            List<FlowPayload> nbFlows = getFlowDump();
            nbFlows.forEach(flow->flows.add(flow.getId()));

            flows.forEach(FlowUtils::cleanUpFlow);

            assertEquals(nbFlows.size() * 2, tpeFlows.size());
            assertEquals(nbFlows.size(), flows.size());

        } catch (Exception exception) {
            System.out.println(String.format("Error during flow deletion: %s", exception.getMessage()));
            exception.printStackTrace();
        }
    }

    private static void cleanUpFlow(String flowId) {
        deleteFlow(flowId);
        if (getFlow(getFlowName(flowId)) != null) {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    public static boolean isTrafficTestsEnabled() {
        boolean isEnabled = Boolean.valueOf(System.getProperty("traffic", "true"));
        System.out.println(String.format("=====> Traffic check is %s", isEnabled ? "enabled" : "disabled"));
        return isEnabled;
    }
}
