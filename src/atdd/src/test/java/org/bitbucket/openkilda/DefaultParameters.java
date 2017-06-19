package org.bitbucket.openkilda;

import static com.google.common.base.MoreObjects.firstNonNull;

public final class DefaultParameters {
    private static final String host = firstNonNull(System.getProperty("kilda.host"), "localhost");
    private static final String mininetPort = firstNonNull(System.getProperty("kilda.mininet.port"), "38080");
    private static final String topologyPort = firstNonNull(System.getProperty("kilda.topology.port"), "80");
    private static final String northboundPort = firstNonNull(System.getProperty("kilda.northbound.port"), "8088");
    public static final String topologyUsername = firstNonNull(System.getProperty("kilda.topology.username"), "kilda");
    public static final String topologyPassword = firstNonNull(System.getProperty("kilda.topology.password"), "kilda");
    public static final String mininetEndpoint = String.format("http://%s:%s", host, mininetPort);
    public static final String topologyEndpoint = String.format("http://%s:%s", host, topologyPort);
    public static final String northboundEndpoint = String.format("http://%s:%s", host, northboundPort);

    static {
        System.out.println(String.format("Mininet Endpoint: %s", mininetEndpoint));
        System.out.println(String.format("Topology Endpoint: %s", topologyEndpoint));
        System.out.println(String.format("Northbound Endpoint: %s", northboundEndpoint));
    }

    private DefaultParameters() {
    }
}
