package org.openkilda;

import static java.lang.String.format;
import static java.util.Base64.getEncoder;
import static org.openkilda.DefaultParameters.mininetEndpoint;
import static org.openkilda.DefaultParameters.topologyEndpoint;
import static org.openkilda.DefaultParameters.topologyPassword;
import static org.openkilda.DefaultParameters.topologyUsername;
import static org.openkilda.DefaultParameters.trafficEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import java.io.IOException;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class LinksUtils {

    private static final String auth = topologyUsername + ":" + topologyPassword;
    private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());

    private LinksUtils() {
    }

    /**
     * Returns links through Topology-Engine-Rest service.
     *
     * @return The JSON document of all flows
     */
    public static List<IslInfoData> dumpLinks() {
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

        try {
            List<IslInfoData> links = new ObjectMapper().readValue(
                    response.readEntity(String.class), new TypeReference<List<IslInfoData>>() {
                    });
            //LOGGER.debug(String.format("====> Data = %s", links));
            return links;

        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to parse the links '%s'.", response.toString()), ex);
        }
    }

    public static boolean islFail(String switchName, String portNo) {
        System.out.println("\n==> Set ISL Discovery Failed");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(trafficEndpoint)
                .path("/cutlink")
                .queryParam("switch", switchName)
                .queryParam("port", portNo)
                .request()
                .post(Entity.json(""));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Set ISL Discovery Failed Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    public static boolean addLink(String srcSwitchName, String destSwitchName) {
        System.out.println("\n==> Adding link");

        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/links")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(
                        String.format("{\"links\":[{\"node1\": \"%s\", \"node2\": \"%s\"}]}",
                                srcSwitchName, destSwitchName)));

        return result.getStatus() == 200;
    }

}
