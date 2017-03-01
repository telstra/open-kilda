package org.bitbucket.openkilda.tools.maxinet.impl;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class MaxinetServer {
	
	@Inject
	@Named("maxinet.host")
	private String host;
	
	@Inject
	@Named("maxinet.port")
	private Integer port;
	
	public MaxinetServer() {
	}

	public String getHost() {
		return host;
	}

	public Integer getPort() {
		return port;
	}
	
	public String getBaseUrl() {
		return "http://" + host + ":" + port + "/";
	}


}
