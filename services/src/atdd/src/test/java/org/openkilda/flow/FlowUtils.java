/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.flow;

import static java.lang.String.format;
import static java.util.Base64.getEncoder;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.openkilda.DefaultParameters.northboundEndpoint;
import static org.openkilda.DefaultParameters.topologyEndpoint;
import static org.openkilda.DefaultParameters.topologyPassword;
import static org.openkilda.DefaultParameters.topologyUsername;

import org.openkilda.DefaultParameters;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.HealthCheck;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.messaging.payload.FeatureTogglePayload;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.dto.BatchResults;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.provider.NeoDriver;
import org.openkilda.pce.provider.PathComputer;
import org.openkilda.pce.provider.UnroutablePathException;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.jackson.JacksonFeature;

import java.io.IOException;
import java.util.ArrayList;
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
    private static final int WAIT_ATTEMPTS = 10;
    private static final int WAIT_DELAY = 2;

    public static final Client clientFactory() {
        Client client = ClientBuilder.newClient(new ClientConfig()).register(JacksonFeature.class);
        return client;
    }

    public static int getHealthCheck() {
        System.out.println("\n==> Northbound Health-Check");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/health-check")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Health-Check Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            System.out.println(format("====> Health-Check = %s",
                    response.readEntity(HealthCheck.class)));
        } else {
            System.out.println(format("====> Error: Health-Check = %s",
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
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Get Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(format("====> Northbound Get Flow = %s", flow));
            return flow;
        } else {
            System.out.println(format("====> Error: Northbound Get Flow = %s",
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
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .put(Entity.json(payload));

        System.out.println(format("===> Request Payload = %s", Entity.json(payload).getEntity()));
        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Create Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(format("====> Northbound Create Flow = %s", flow));
            return flow;
        } else {
            System.out.println(format("====> Error: Northbound Create Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Updates flow through Northbound service.
     *
     * @param flowId flow id
     * @param payload flow JSON data
     * @return The JSON document of the created flow
     */
    public static FlowPayload updateFlow(final String flowId, final FlowPayload payload) {
        System.out.println("\n==> Northbound Update Flow");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .put(Entity.json(payload));

        System.out.println(format("===> Request Payload = %s", Entity.json(payload).getEntity()));
        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Update Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(format("====> Northbound Update Flow = %s", flow));
            return flow;
        } else {
            System.out.println(format("====> Error: Northbound Update Flow = %s",
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
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .delete();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Delete Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPayload flow = response.readEntity(FlowPayload.class);
            System.out.println(format("====> Northbound Delete Flow = %s", flow));
            return flow;
        } else {
            System.out.println(format("====> Error: Northbound Delete Flow = %s",
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
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/path")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Get Flow Path Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            FlowPathPayload flowPath = response.readEntity(FlowPathPayload.class);
            System.out.println(format("====> Northbound Get Flow Path = %s", flowPath));
            return flowPath;
        } else {
            System.out.println(format("====> Error: Northbound Get Flow Path = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Poll flow status via getFlowStatus calls until it become equal to expected. Or until timeout.
     *
     * TODO: Why do we loop for 10 and sleep for 2? (ie why what for 20 seconds for flow state?)
     *
     * @return last result received from getFlowStatus (can be null)
     */
    public static FlowIdStatusPayload waitFlowStatus(String flowName, FlowState expected) throws InterruptedException {
        FlowIdStatusPayload current = null;
        for (int i = 0; i < WAIT_ATTEMPTS; i++) {
            current = getFlowStatus(flowName);
            if (current != null && expected.equals(current.getStatus())) {
                break;
            }
            TimeUnit.SECONDS.sleep(WAIT_DELAY);
        }
        return current;
    }

    /**
     * call doGetFlowStatusRequest until it got success codes 2xx. If it got not 2xx code and not 404 code it
     * raise error. If it get 404 code it ends successfully.
     */
    public static void waitFlowDeletion(String flowId) throws InterruptedException, FlowOperationException {
        for (int attempt = 0; attempt < WAIT_ATTEMPTS; attempt += 1) {
            Response response = doGetFlowStatusRequest(flowId);
            int status = response.getStatus();

            if (200 <= status && status < 300) {
                TimeUnit.SECONDS.sleep(WAIT_DELAY);
                continue;
            }

            if (status != 404) {
                throw new FlowOperationException(
                        response,
                        format("Flow status request for flow %s ens with %d", flowId, status));
            }

            break;
        }
    }

    /**
     * Gets flow status through Northbound service.
     *
     * @param flowId flow id
     * @return The JSON document of the specified flow status
     */
    public static FlowIdStatusPayload getFlowStatus(final String flowId) {
        Response response = doGetFlowStatusRequest(flowId);

        int responseCode = response.getStatus();
        if (responseCode != 200) {
            System.out.println(format("====> Error: Northbound Get Flow Status = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
        return response.readEntity(FlowIdStatusPayload.class);
    }

    private static Response doGetFlowStatusRequest(final String flowId) {
        System.out.println("\n==> Northbound Get Flow Status");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/status")
                .path("{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Get Flow Status Time: %,.3f", getTimeDuration(current)));

        int status = response.getStatus();
        System.out.println(format("====> Northbound Get Flow Status = %s", status));

        return response;
    }

    /**
     * Gets flows dump through Northbound service.
     *
     * @return The JSON document of the dump flows
     */
    public static List<FlowPayload> getFlowDump() {
        System.out.println("\n==> Northbound Get Flow Dump");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Get Flow Dump Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            List<FlowPayload> flows = response.readEntity(new GenericType<List<FlowPayload>>() {
            });
            System.out.println(format("====> Northbound Get Flow Dump = %d", flows.size()));
            return flows;
        } else {
            System.out.println(format("====> Error: Northbound Get Flow Dump = %s",
                    response.readEntity(MessageError.class)));
            return Collections.emptyList();
        }
    }

    /**
     * Returns flows through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<Flow> dumpFlows() {
        System.out.println("\n==> Topology-Engine Dump Flows");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/flows")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Topology-Engine Dump Flows Time: %,.3f", getTimeDuration(current)));

        try {
            List<Flow> flows = new ObjectMapper().readValue(response.readEntity(String.class),
                    new TypeReference<List<Flow>>() {
                    });
            System.out.println(format("====> Topology-Engine Dump Flows = %d", flows.size()));

            return flows;

        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to parse the flows '%s'.", response.toString()), ex);
        }
    }

    /**
     * Returns link available bandwidth through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static Integer getLinkBandwidth(final String src_switch, final String src_port) {
        System.out.println("\n==> Topology-Engine Link Bandwidth");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

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

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Topology-Engine Link Bandwidth Time: %,.3f", getTimeDuration(current)));

        try {
            Integer bandwidth = new ObjectMapper().readValue(response.readEntity(String.class), Integer.class);
            System.out.println(format("====> Link switch=%s port=%s bandwidth=%d", src_switch, src_port, bandwidth));

            return bandwidth;
        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to parse the links '%s'.", response.toString()), ex);
        }
    }

    public static void restoreFlows() {
        System.out.println("\n==> Topology-Engine Restore Flows");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/flows/restore")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Topology-Engine Restore Flows Time: %,.3f", getTimeDuration(current)));
    }

    /**
     * Cleanups all flows.
     */
    public static void cleanupFlows() {
        try {
            Set<String> flows = new HashSet<>();

            // TODO: This method started with getting counts and compariing, but that shouldn't be
            //          the responsibility of this method given its name - cleanupFlows.
            //          So, the TODO is to determine whether this code exists elsewhere in tests,
            //          and if not, move it somewhere after, or part of, create test.

            // Get the flows through the NB API
            List<FlowPayload> nbFlows = getFlowDump();
            System.out.println(format("=====> Cleanup Flows, nbflow count = %d",
                    nbFlows.size()));

            nbFlows.forEach(flow -> flows.add(flow.getId()));

            // Get the flows through the TE Rest API ... loop until the math works out.
            List<Flow> tpeFlows = new ArrayList<>();
            for (int i = 0; i < 10; ++i) {
                tpeFlows = dumpFlows();
                if (tpeFlows.size() == nbFlows.size() * 2) {
                    tpeFlows.forEach(flow -> flows.add(flow.getFlowId()));
                    break;
                }
                TimeUnit.SECONDS.sleep(2);
            }
            System.out.println(format("=====> Cleanup Flows, tpeFlows count = %d",
                    tpeFlows.size()));

            // Delete all the flows
            flows.forEach(FlowUtils::deleteFlow);

            // Wait for them to become zero
            int nb_count = -1;
            int ter_count = -1;
            for (int i = 0; i < 10; ++i) {
                TimeUnit.SECONDS.sleep(2);
                nb_count = dumpFlows().size();
                ter_count = getFlowDump().size();
                if (nb_count == 0 && ter_count == 0) {
                    break;
                }
            }

            assertEquals(0, nb_count);
            assertEquals(0, ter_count);

// (crimi) - it is unclear why we are doing a count validation here .. it makes sense to do this
// in the creation. But on cleanup, we just want things to be zero.
//            assertEquals(nbFlows.size() * 2, tpeFlows.size());
//            assertEquals(nbFlows.size(), flows.size());

        } catch (Exception exception) {
            System.out.println(format("Error during flow deletion: %s", exception.getMessage()));
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
        return format("%s-%s", flowId, FEATURE_TIME);
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
    public static ImmutablePair<PathInfoData, PathInfoData> getFlowPath(Flow flow)
            throws InterruptedException, UnroutablePathException, RecoverableException {
        Thread.sleep(1000);
        PathComputer pathComputer = new NeoDriver(DefaultParameters.neoAuth.getDriver());
        return pathComputer.getPath(flow, PathComputer.Strategy.COST);
    }

    public static boolean isTrafficTestsEnabled() {
        boolean isEnabled = Boolean.valueOf(System.getProperty("traffic", "true"));
        System.out.println(format("\n=====> Traffic check is %s", isEnabled ? "enabled" : "disabled"));
        return isEnabled;
    }

    public static FeatureTogglePayload updateFeaturesStatus(FeatureTogglePayload desired) {
        System.out.println("\n==> toggle features status");

        Client client = clientFactory();

        Response response;
        response = client
                .target(northboundEndpoint)
                .path("/api/v1/features")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .post(Entity.json(desired));

        System.out.println(format("===> Response = %s", response.toString()));

        if (response.getStatus() != 200) {
            System.out.println(format("====> Error: Northbound Create Flow = POST status: %s", response.getStatus()));
            return null;
        }

        response = client
                .target(northboundEndpoint)
                .path("/api/v1/features")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();
        if (response.getStatus() != 200) {
            System.out.println(format("====> Error: Northbound Create Flow = GET status: %s", response.getStatus()));
            return null;
        }

        return response.readEntity(FeatureTogglePayload.class);
    }

    /**
     * Perform the flow cache synchronization (via Northbound service).
     */
    public static FlowCacheSyncResults synchFlowCache() {
        System.out.println("\n==> Northbound Sync Flow Cache");

        long current = System.currentTimeMillis();
        Client client = clientFactory();
        // Enable support of PATCH method
        client.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/cache")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .method("PATCH");

        System.out.println(format("===> Northbound Sync Flow Cache Time: %,.3f", getTimeDuration(current)));

        if (response.getStatus() != 200) {
            System.out.println(format("====> Error: Northbound Sync Flow Cache = PATCH status: %s",
                    response.getStatus()));
            return null;
        }

        System.out.println(format("===> Response = %s", response.toString()));
        return response.readEntity(FlowCacheSyncResults.class);
    }

    /**
     * Perform the flow cache invalidation (via Northbound service).
     */
    public static FlowCacheSyncResults invalidateFlowCache() {
        System.out.println("\n==> Northbound Invalidate Flow Cache");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/cache")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .delete();

        System.out.println(format("===> Northbound Invalidate Flow Cache Time: %,.3f", getTimeDuration(current)));

        if (response.getStatus() != 200) {
            System.out.println(format("====> Error: Northbound Invalidate Flow Cache = PATCH status: %s", response.getStatus()));
            return null;
        }

        System.out.println(format("===> Response = %s", response.toString()));
        return response.readEntity(FlowCacheSyncResults.class);
    }

    /**
     * Deletes flow through TopologyEngine service.
     */
    public static boolean deleteFlowViaTE(final String flowId) {
        System.out.println("\n==> TopologyEngine Delete Flow");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/flow/{flowid}")
                .resolveTemplate("flowid", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .delete();

        System.out.println(format("===> TopologyEngine Delete Flow Time: %,.3f", getTimeDuration(current)));

        int status = response.getStatus();
        if (status != 200) {
            System.out.println(String.format("====> Error: TopologyEngine Delete Flow = %s",
                    response.readEntity(String.class)));
            return false;
        }

        System.out.println(format("====> TopologyEngine Delete Flow = %s", response.readEntity(String.class)));
        return true;
    }

    /**
     * Validate the flow path and rules (e.g. whether properly installed).
     */
    public static List<FlowValidationDto> validateFlow(final String flowId) {
        System.out.println("\n==> Northbound Validate Flow");

        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/flows/{flow-id}/validate")
                .resolveTemplate("flow-id", flowId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            List<FlowValidationDto> flowDiscrepancy =
                    response.readEntity(new GenericType<List<FlowValidationDto>>() {});
            System.out.println(format("====> Northbound Validate Flow = %s", flowDiscrepancy));
            return flowDiscrepancy;
        } else {
            System.out.println(format("====> Error: Northbound Validate Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Push a flow through Northbound service.
     *
     * @param flowInfo the flow definition
     * @param propagate whether propagate data to switches or not
     * @return the result of the push operation
     */
    public static BatchResults pushFlow(FlowInfoData flowInfo, boolean propagate)
            throws JsonProcessingException {
        System.out.println("\n==> Northbound Push Flow");

        long current = System.currentTimeMillis();
        Client client = clientFactory();

        String correlationId = String.valueOf(current);
        flowInfo.setCorrelationId(correlationId);

        String requestJson = new ObjectMapper().writerFor(new TypeReference<List<FlowInfoData>>() { })
                .writeValueAsString(singletonList(flowInfo));

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/push/flows")
                .queryParam("propagate", propagate)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, correlationId)
                .put(Entity.json(requestJson));

        System.out.println(format("===> Request Payload = %s", requestJson));
        System.out.println(format("===> Response = %s", response.toString()));
        System.out.println(format("===> Northbound Push Flow Time: %,.3f", getTimeDuration(current)));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            BatchResults result = response.readEntity(BatchResults.class);
            System.out.println(format("====> Northbound Push Flow = %s", result));
            return result;
        } else {
            System.out.println(format("====> Error: Northbound Push Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }
}
