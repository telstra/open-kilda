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
