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

package org.openkilda.topo;

import static org.openkilda.DefaultParameters.mininetEndpoint;
import static org.openkilda.DefaultParameters.topologyEndpoint;
import static org.openkilda.flow.FlowUtils.getTimeDuration;

import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;

import org.glassfish.jersey.client.ClientConfig;

import java.io.IOException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
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

        //
        // TODO: mininet_rest has been re-written, but not create_random_linear_topology.
        //          It is unclear if this code path is still used ATM (could be useful for scale
        //          testing, but possibly not in its current form.
        //

        boolean refactored = false;
        if (!refactored)
            throw new UnsupportedOperationException("This code needs refactoring");

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

    public static FlowPair<Flow, Flow> GetFlow(String flowId) {
        System.out.println("\n==> Topology-Engine Get Flow");

        Client client = ClientBuilder.newClient(new ClientConfig());
        Response response = client
            .target(topologyEndpoint)
            .path("/api/v1/topology/flows/")
            .path(flowId)
            .request(MediaType.APPLICATION_JSON)
            .get();

        int status = response.getStatus();
        if (status != 200) {
            System.out.println(String.format("====> Error: Topology-Engine Get Flow = %s",
                    response.readEntity(MessageError.class)));
            return null;
        }

        FlowPair<Flow, Flow> result = response.readEntity(new GenericType<FlowPair<Flow, Flow>>() {});
        System.out.println(String.format("====> Topology-Engine Get Flow = %s", result));
        return result;
    }

    // FIXME(surabujin): garbage
    public static void main(String[] args) throws IOException {
        //TopologyHelp.DeleteTopology();
        //URL url = Resources.getResource("topologies/partial-topology.json");
        //String doc = Resources.toString(url, Charsets.UTF_8);
        //TopologyHelp.CreateTopology(doc);

        System.out.println("GetTopology(): = " + TopologyHelp.GetTopology());
        System.out.println("ClearTopology(): = " + TopologyHelp.ClearTopology());
    }
}
