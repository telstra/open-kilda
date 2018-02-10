package org.openkilda;

import static java.util.Base64.getEncoder;
import static org.openkilda.DefaultParameters.mininetEndpoint;
import static org.openkilda.DefaultParameters.topologyEndpoint;
import static org.openkilda.DefaultParameters.topologyPassword;
import static org.openkilda.DefaultParameters.topologyUsername;
import static org.openkilda.DefaultParameters.trafficEndpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;
import org.openkilda.messaging.info.event.SwitchInfoData;

import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
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

        System.out.println(String.format("===> Response = %s", result.toString()));
        return result.getStatus() == 200;
    }

    /**
     * Turns on switch.
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

        System.out.println(String.format("===> Response = %s", result.toString()));
        return result.getStatus() == 200;
    }


    /**
     * Returns Switches through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<SwitchInfoData> dumpSwitches() throws Exception {
        System.out.println("\n==> Topology-Engine Dump Switches");

        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/switches")
                .request()
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));

        List<SwitchInfoData> switches = new ObjectMapper().readValue(
                response.readEntity(String.class), new TypeReference<List<SwitchInfoData>>() {});
        //System.out.println(String.format("====> Data = %s", switches));

        return switches;
    }

    /**
     * Adds new switch into mininet topology.
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
                        String.format("{\"switches\":[{\"name\": \"%s\", \"dpid\": \"%s\"}]}",
                                switchName, dpid)));

        return result.getStatus() == 200;
    }


}
