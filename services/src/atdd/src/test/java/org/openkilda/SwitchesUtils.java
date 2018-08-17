package org.openkilda;

import static java.lang.String.format;
import static java.util.Base64.getEncoder;
import static org.openkilda.DefaultParameters.mininetEndpoint;
import static org.openkilda.DefaultParameters.northboundEndpoint;
import static org.openkilda.DefaultParameters.topologyPassword;
import static org.openkilda.DefaultParameters.topologyUsername;
import static org.openkilda.DefaultParameters.trafficEndpoint;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.switches.DeleteRulesAction;
import org.openkilda.messaging.command.switches.PortStatus;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.northbound.dto.switches.PortDto;
import org.openkilda.northbound.dto.switches.RulesSyncResult;
import org.openkilda.northbound.dto.switches.RulesValidationResult;
import org.openkilda.northbound.dto.switches.SwitchDto;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;

import java.io.IOException;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class SwitchesUtils {

    private static final String auth = topologyUsername + ":" + topologyPassword;
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());

    private SwitchesUtils() {
    }

    /**
     * Turns off switch.
     *
     * @param switchName switch name.
     * @return true if result is success.
     */
    public static boolean knockoutSwitch(String switchName) {
        System.out.println("\n==> Knockout switch");

        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/knockoutswitch")
                .queryParam("switch", switchName)
                .request()
                .post(Entity.json(""));

        System.out.println(format("===> Response = %s", result.toString()));
        return result.getStatus() == 200;
    }

    /**
     * Turns on switch.
     *
     * @param switchName switch name.
     * @return true if result is success.
     */
    public static boolean reviveSwitch(String switchName) {
        System.out.println("\n==> Revive switch");

        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/reviveswitch")
                .queryParam("switch", switchName)
                .queryParam("controller", "tcp:kilda:6653")
                .request()
                .post(Entity.json(""));

        System.out.println(format("===> Response = %s", result.toString()));
        return result.getStatus() == 200;
    }


    /**
     * Returns Switches through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<SwitchDto> dumpSwitches() {
        System.out.println("\n==> Topology-Engine Dump Switches");

        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/switches")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));

        try {
            List<SwitchDto> switches = new ObjectMapper().readValue(
                    response.readEntity(String.class), new TypeReference<List<SwitchDto>>() {
                    });
            //System.out.println(String.format("====> Data = %s", switches));

            return switches;

        } catch (IOException ex) {
            throw new TopologyProcessingException(
                    format("Unable to parse the switches '%s'.", response.toString()), ex);
        }
    }

    /**
     * Adds new switch into mininet topology.
     *
     * @param switchName switch name.
     * @param dpid data path id.
     * @return true if switch was added successfully, otherwise - false.
     */
    public static boolean addSwitch(String switchName, String dpid) {
        System.out.println("\n==> Adding link");

        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/switch")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(
                        format("{\"switches\":[{\"name\": \"%s\", \"dpid\": \"%s\"}]}",
                                switchName, dpid)));

        return result.getStatus() == 200;
    }

    public static List<Long> deleteSwitchRules(String switchId, DeleteRulesAction deleteAction) {
        return deleteSwitchRules(switchId, deleteAction, null, null, null);
    }

    public static List<Long> deleteSwitchRules(String switchId, Integer inPort, Integer inVlan) {
        return deleteSwitchRules(switchId, null, inPort, inVlan, null);
    }

    public static List<Long> deleteSwitchRules(String switchId, Integer outPort) {
        return deleteSwitchRules(switchId, null, null, null, outPort);
    }

    /**
     * Delete switch rules through Northbound service.
     */
    public static List<Long> deleteSwitchRules(String switchId, DeleteRulesAction deleteAction,
                                               Integer inPort, Integer inVlan, Integer outPort) {
        System.out.println("\n==> Northbound Delete Switch Rules");

        Client client = ClientBuilder.newClient(new ClientConfig());

        WebTarget requestBuilder = client
                .target(northboundEndpoint)
                .path("/api/v1/switches/")
                .path("{switch-id}")
                .path("rules")
                .resolveTemplate("switch-id", switchId);

        if (deleteAction != null) {
            requestBuilder = requestBuilder.queryParam("delete-action", deleteAction);
        }
        if (inPort != null) {
            requestBuilder = requestBuilder.queryParam("in-port", inPort);
        }
        if (inVlan != null) {
            requestBuilder = requestBuilder.queryParam("in-vlan", inVlan);
        }
        if (outPort != null) {
            requestBuilder = requestBuilder.queryParam("out-port", outPort);
        }

        Response response = requestBuilder
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .header(Utils.EXTRA_AUTH, String.valueOf(System.currentTimeMillis()))
                .delete();

        System.out.println(format("===> Response = %s", response.toString()));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            List<Long> cookies = response.readEntity(new GenericType<List<Long>>() {
            });
            System.out.println(format("====> Northbound Delete Switch Rules = %d", cookies.size()));
            return cookies;
        } else {
            System.out.println(format("====> Error: Northbound Delete Switch Rules = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Returns rules of a switch (via Northbound service).
     */
    public static List<FlowEntry> dumpSwitchRules(String switchId) {
        System.out.println("\n==> Northbound Dump Switch Rules");

        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/switches/{switch-id}/rules")
                .resolveTemplate("switch-id", switchId)

                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            SwitchFlowEntries rules = response.readEntity(SwitchFlowEntries.class);
            System.out.println(format("====> Northbound Dump Switch Rules = %s", rules.getFlowEntries()));
            return rules.getFlowEntries();
        } else {
            System.out.println(format("====> Error: Northbound Dump Switch Rules = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Validate rules of a switch against the flows in Neo4J (via Northbound service).
     */
    public static RulesValidationResult validateSwitchRules(String switchId) {
        System.out.println("\n==> Northbound Validate Switch Rules");

        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/switches/{switch-id}/rules/validate")
                .resolveTemplate("switch-id", switchId)

                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            RulesValidationResult rules = response.readEntity(RulesValidationResult.class);
            System.out.println(format("====> Northbound Validate Switch Rules = %s", rules));
            return rules;
        } else {
            System.out.println(format("====> Error: Northbound Validate Switch Rules = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Synchronize rules of a switch with the flows in Neo4J (via Northbound service).
     */
    public static RulesSyncResult synchronizeSwitchRules(String switchId) {
        System.out.println("\n==> Northbound Synchronize Switch Rules");

        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/switches/{switch-id}/rules/synchronize")
                .resolveTemplate("switch-id", switchId)

                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(format("===> Response = %s", response.toString()));

        int responseCode = response.getStatus();
        if (responseCode == 200) {
            RulesSyncResult rules = response.readEntity(RulesSyncResult.class);
            System.out.println(format("====> Northbound Synchronize Switch Rules = %s", rules));
            return rules;
        } else {
            System.out.println(format("====> Error: Northbound Synchronize Switch Rules = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }
    }

    /**
     * Update port status.
     */
    public static PortDto changeSwitchPortStatus(String switchName, int port, PortStatus status) {
        System.out.println("\n==> Change port state");

        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client.target(northboundEndpoint)
                .path("/api/v1/switches/{switch-id}/port/{port-no}/config")
                .resolveTemplate("switch-id", switchName)
                .resolveTemplate("port-no", port)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .put(Entity.json(format("{\"status\": \"%s\"}", status)));

        System.out.println(format("===> Response = %s", result.toString()));

        int responseCode = result.getStatus();
        if (responseCode == 200) {
            PortDto portDto = result.readEntity(PortDto.class);
            System.out.println(format("====> Success" + " = %s", portDto));
            return portDto;
        } else {
            System.out.println(format("====> Error = %s", result.readEntity(MessageError.class)));
            return null;
        }
    }
}
