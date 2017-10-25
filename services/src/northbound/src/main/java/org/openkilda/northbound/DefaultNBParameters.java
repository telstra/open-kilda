package org.openkilda.northbound;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Base64.getEncoder;

public class DefaultNBParameters {
	
	public static final String host = firstNonNull(System.getProperty("kilda.host"),"localhost");

	public static final String opentsdbPort = firstNonNull(System.getProperty("kilda.opentsdb.port"), "4242");
	public static final String northboundPort = firstNonNull(System.getProperty("kilda.northbound.port"), "8088");
	public static final String topologyUsername = firstNonNull(System.getProperty("kilda.topology.username"), "kilda");
	public static final String topologyPassword = firstNonNull(System.getProperty("kilda.topology.password"), "kilda");
	public static final String auth = topologyUsername + ":" + topologyPassword;
	public static final String opentsdbEndpoint = String.format("http://%s:%s", host, opentsdbPort);
	public static final String northboundEndpoint = String.format("http://%s:%s", host, northboundPort);
	public static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());
}
