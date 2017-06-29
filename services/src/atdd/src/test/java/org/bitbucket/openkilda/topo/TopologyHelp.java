package org.bitbucket.openkilda.topo;

import static org.bitbucket.openkilda.DefaultParameters.mininetEndpoint;
import static org.bitbucket.openkilda.DefaultParameters.topologyEndpoint;

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
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/cleanup")
                .request(MediaType.APPLICATION_JSON)
                .post(null);

        System.out.println("==> DeleteTopology Time: " + (double) (((System.currentTimeMillis() -
                current)
                / 1000.0)));

        return result.getStatus() == 200;
    }

    /**
     * Creates the topology through Mininet.
     *
     * @param json - the json doc that is suitable for the mininet API
     */
    public static boolean CreateMininetTopology(String json) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json, MediaType.APPLICATION_JSON));

        System.out.println("\n== Create Topology\n==> result = " + result);
        System.out.println("==> CreateTopology Time: " + (double) (((System.currentTimeMillis() -
                current)
                / 1000.0)));

        return result.getStatus() == 200;
    }

    public static boolean TestMininetCreate(String json) {
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client
                .target(mininetEndpoint)
                .path("/create_random_linear_topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json, MediaType.APPLICATION_JSON));

        System.out.println("\n== Mininet Create Random Topology\n==> result = " + result);
        System.out.println("==> Mininet Create Random Topology Time: " + (double) (((System
                .currentTimeMillis() -
                current)
                / 1000.0)));

        return result.getStatus() == 200;
    }

    /**
     * NB: This method calls TE, not Mininet
     *
     * @return The JSON document of the Topology from the Topology Engine
     */
    public static String GetTopology() {

        Client client = ClientBuilder.newClient(new ClientConfig());

        String result = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/network")
                .request()
                .get(String.class);

        return result;
    }

    /**
     * NB: This method calls TE, not Mininet
     *
     * @return The JSON document of the Topology from the Topology Engine
     */
    public static String ClearTopology() {

        Client client = ClientBuilder.newClient(new ClientConfig());

        String result = client
                .target(topologyEndpoint)
                .path("/api/v1/topology/clear")
                .request()
                .get(String.class);

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
