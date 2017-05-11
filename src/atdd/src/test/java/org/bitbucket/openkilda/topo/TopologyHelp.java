package org.bitbucket.openkilda.topo;

import org.glassfish.jersey.client.ClientConfig;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;


/**
 * Helper methods for doing Topology tests.
 */
public class TopologyHelp {

    public static boolean DeleteMininetTopology(String mini_ip, String mini_port){
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client.target("http://"+mini_ip + ":" + mini_port).path("/cleanup")
                .request(MediaType.APPLICATION_JSON)
                .post(null);

        System.out.println("==> DeleteTopology Time: " + (double)(((System.currentTimeMillis() -
                current)
                / 1000.0)));

        return result.getStatus() == 200;
    }

    public static boolean DeleteMininetTopology() {
        return DeleteMininetTopology("localhost","38080");
    }

    /**
     * Creates the topology through Mininet.
     *
     * @param json - the json doc that is suitable for the mininet API
     */
    public static boolean CreateMininetTopology(String json){
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client.target("http://localhost:38080").path("/topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json,MediaType.APPLICATION_JSON));

        System.out.println("\n== Create Topology\n==> result = " + result);
        System.out.println("==> CreateTopology Time: " + (double)(((System.currentTimeMillis() -
                current)
                / 1000.0)));

        return result.getStatus() == 200;
    }


    public static boolean TestMininetCreate(String json){
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client.target("http://localhost:38080").path("/create_random_linear_topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(json,MediaType.APPLICATION_JSON));

        System.out.println("\n== Mininet Create Random Topology\n==> result = " + result);
        System.out.println("==> Mininet Create Random Topology Time: " + (double)(((System
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
    public static String GetTopology(){

        Client client = ClientBuilder.newClient(new ClientConfig());

        String result = client.target("http://localhost:80").path("/api/v1/topology/network")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);

        return result;
    }


    /**
     * NB: This method calls TE, not Mininet
     *
     * @return The JSON document of the Topology from the Topology Engine
     */
    public static String ClearTopology(String endpoint){

        Client client = ClientBuilder.newClient(new ClientConfig());

        String result = client.target("http://"+endpoint+":80").path("/api/v1/topology/clear")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);

        return result;
    }



    public static void main(String[] args) throws IOException {
        // TopologyHelp.DeleteTopology();
//        URL url = Resources.getResource("topologies/partial-topology.json");
//        String doc = Resources.toString(url, Charsets.UTF_8);
//        TopologyHelp.CreateTopology(doc);

        System.out.println("GetTopology(): = " + TopologyHelp.GetTopology());
        System.out.println("ClearTopology(): = " + TopologyHelp.ClearTopology("localhost"));
    }
}
