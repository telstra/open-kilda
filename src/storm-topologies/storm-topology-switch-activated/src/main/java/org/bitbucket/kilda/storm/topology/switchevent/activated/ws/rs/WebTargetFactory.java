package org.bitbucket.kilda.storm.topology.switchevent.activated.ws.rs;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class WebTargetFactory implements IWebTargetFactory {
	
	private static final Integer CONNECTION_TIMEOUT_MS = 5000;
	
	private final String uri;
	
	public WebTargetFactory(String uri) {
		this.uri = uri;
	}

	@Override
	public WebTarget create() {
		ClientConfig configuration = new ClientConfig();
		configuration = configuration.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT_MS);
		configuration = configuration.property(ClientProperties.READ_TIMEOUT, CONNECTION_TIMEOUT_MS);
		Client client = ClientBuilder.newClient(configuration).register(JacksonJsonProvider.class);
		return client.target(uri);
	}

}
