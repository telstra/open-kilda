package org.bitbucket.openkilda.smoke;

import org.glassfish.jersey.client.ClientConfig;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import static org.junit.Assert.assertEquals;

/**
 * Created by carmine on 3/2/17.
 */
public class TopologyEngineSmokeTest  {

    @Test
    public void testServiceUp(){

        Client client = ClientBuilder.newClient(new ClientConfig());

        String entity = client.target("http://127.0.0.1:80").path("/")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);
        System.out.println(entity);

        assertEquals("Default, initial, response from TopologyEngine", "[]",entity);
    }
}
