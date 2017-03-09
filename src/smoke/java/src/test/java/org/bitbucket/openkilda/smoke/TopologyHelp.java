package org.bitbucket.openkilda.smoke;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.Topology;
import org.glassfish.jersey.client.ClientConfig;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;


/**
 * Helper methods for doing Topology tests.
 */
public class TopologyHelp {

    public static boolean DeleteTopology(){
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client.target("http://localhost:38080").path("/cleanup")
                .request(MediaType.APPLICATION_JSON)
                .post(null);

        System.out.println("\n== Delete Topology\n==> result = " + result);
        System.out.println("==> DeleteTopology Time: " + (double)(((System.currentTimeMillis() -
                current)
                / 1000.0)));

        return result.getStatus() == 200;
    }

    /**
     * Creates the topology through Mininet.
     *
     * @param mininetJson - the json doc that is suitable for the mininet API
     */
    public static boolean CreateTopology(String mininetJson){
        long current = System.currentTimeMillis();
        Client client = ClientBuilder.newClient(new ClientConfig());
        Response result = client.target("http://localhost:38080").path("/topology")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(mininetJson,MediaType.APPLICATION_JSON));

        System.out.println("\n== Create Topology\n==> result = " + result);
        System.out.println("==> CreateTopology Time: " + (double)(((System.currentTimeMillis() -
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
    public static String ClearTopology(){

        Client client = ClientBuilder.newClient(new ClientConfig());

        String result = client.target("http://localhost:80").path("/api/v1/topology/clear")
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
        System.out.println("ClearTopology(): = " + TopologyHelp.ClearTopology());
    }
}
