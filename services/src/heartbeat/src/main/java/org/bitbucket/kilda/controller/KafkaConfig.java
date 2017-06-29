package org.bitbucket.kilda.controller;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class KafkaConfig {

	private String host;
	
	private Integer port;
	
	@Inject
	public KafkaConfig(@Named("kafka.host") String host, @Named("kafka.port") Integer port) {
		this.host = host;
		this.port = port;
	}
	
	public String url() {
		return host + ":" + port;
	}
	
}
