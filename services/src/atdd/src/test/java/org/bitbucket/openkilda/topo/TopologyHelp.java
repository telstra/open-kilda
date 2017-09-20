package org.bitbucket.openkilda.topo;

import static org.bitbucket.openkilda.DefaultParameters.mininetEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyEndpoint;
import static org.bitbucket.openkilda.flow.FlowUtils.getTimeDuration;

import org.glassfish.jersey.client.ClientConfig;

import java.io.IOException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Helper methods for doing Topology tests.
 */
public class TopologyHelp {
    public static boolean DeleteMininetTopology() {
        System.out.println("\n==> Delete Mininet Topology");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/cleanup")
                .request(MediaType.APPLICATION_JSON)
                .post(null);

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Delete Mininet Topology Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    /**
     * Creates the topology through Mininet.
     *
     * @param json - the json doc that is suitable for the mininet API
     */
    public static boolean CreateMininetTopology(String json) {
        System.out.println("\n==> Create Mininet Topology");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json, MediaType.APPLICATION_JSON));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Create Mininet Topology Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    public static boolean TestMininetCreate(String json) {
        System.out.println("\n==> Create Mininet Random Topology");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/create_random_linear_topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json, MediaType.APPLICATION_JSON));

        System.out.println(String.format("===> Response = %s", result.toString()));
        System.out.println(String.format("===> Create Mininet Random Topology Time: %,.3f", getTimeDuration(current)));

        return result.getStatus() == 200;
    }

    /**
     * NB: This method calls TE, not Mininet
     *
     * @return The JSON document of the Topology from the Topology Engine
     */
    public static String GetTopology() {
        System.out.println("\n==> Get Topology-Engine Topology");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/network")
                .request()
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Get Topology-Engine Topology Time: %,.3f", getTimeDuration(current)));
        String result = response.readEntity(String.class);
        System.out.println(String.format("====> Topology-Engine Topology = %s", result));

        return result;
    }

    /**
     * NB: This method calls TE, not Mininet
     *
     * @return The JSON document of the Topology from the Topology Engine
     */
    public static String ClearTopology() {
        System.out.println("\n==> Clear Topology-Engine Topology");

        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response response = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/clear")
                .request()
                .get();

        System.out.println(String.format("===> Response = %s", response.toString()));
        System.out.println(String.format("===> Clear Topology-Engine Topology Time: %,.3f", getTimeDuration(current)));
        String result = response.readEntity(String.class);
        System.out.println(String.format("====> Topology-Engine Topology = %s", result));
        return result;
    }

    public static void main(String[] args) throws IOException {
        //TopologyHelp.DeleteTopology();
        //URL url = Resources.getResource("topologies/partial-topology.json");
        //String doc = Resources.toString(url, Charsets.UTF_8);
        //TopologyHelp.CreateTopology(doc);

        System.out.println("GetTopology(): = " + TopologyHelp.GetTopology());
        System.out.println("ClearTopology(): = " + TopologyHelp.ClearTopology());
    }
}
