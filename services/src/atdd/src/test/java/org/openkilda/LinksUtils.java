package org.openkilda;

import static java.lang.String.format;
import static java.util.Base64.getEncoder;
import static org.openkilda.DefaultParameters.mininetEndpoint;
import static org.openkilda.DefaultParameters.northboundEndpoint;
import static org.openkilda.DefaultParameters.topologyPassword;
import static org.openkilda.DefaultParameters.topologyUsername;
import static org.openkilda.DefaultParameters.trafficEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;

import org.openkilda.messaging.Utils;
import org.openkilda.northbound.dto.links.LinkDto;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.glassfish.jersey.client.ClientConfig;

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
    public static List<LinkDto> dumpLinks() {
        System.out.println("\n==> Northbound Dump Links");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());

        Response response = client
                .target(northboundEndpoint)
                .path("/api/v1/links")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
                .header(Utils.CORRELATION_ID, String.valueOf(System.currentTimeMillis()))
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Topology-Engine Dump Links Time: %,.3f", getTimeDuration(current)));

        try {
            List<LinkDto> links = new ObjectMapper().readValue(
                    response.readEntity(String.class), new TypeReference<List<LinkDto>>() {
                    });

            //LOGGER.debug(String.format("====> Data = %s", links));
            return links;

        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to parse the links '%s'.", response.toString()), ex);
        }
    }

    /**
     * Cut the ISL which goes from specific switch and port.
     */
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

    /**
     * Creates link between two switches.
     */
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
