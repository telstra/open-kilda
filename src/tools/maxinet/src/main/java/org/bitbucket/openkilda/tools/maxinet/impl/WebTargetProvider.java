package org.bitbucket.openkilda.tools.maxinet.impl;

import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

public class WebTargetProvider implements Provider<WebTarget> {

	private static final Integer CONNECTION_TIMEOUT_MS = 5000;

	@Override
	public WebTarget get() {
		ClientConfig configuration = new ClientConfig();
		configuration = configuration.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT_MS);
		configuration = configuration.property(ClientProperties.READ_TIMEOUT, CONNECTION_TIMEOUT_MS);
		Client client = ClientBuilder.newClient(configuration).register(JacksonJsonProvider.class)
				.register(ErrorResponseFilter.class);
		return client.target("http://localhost:38081");
	}

}
