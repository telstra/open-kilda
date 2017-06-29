package org.bitbucket.kilda.storm.topology.switchevent.activated.mock.server;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class TopologyEngineMockServer {

	public static void main(String[] args)  {		
		URI baseUri = UriBuilder.fromUri("http://localhost/").port(9991).build();
	    ResourceConfig config = new ResourceConfig(TopologyEngineResource.class);
	    config.register(JsonProcessingFeature.class);
	    HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);
	}
	
}