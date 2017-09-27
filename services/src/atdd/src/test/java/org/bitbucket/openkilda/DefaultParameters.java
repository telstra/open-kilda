package org.bitbucket.openkilda;

import static com.google.common.base.MoreObjects.firstNonNull;

import org.bitbucket.openkilda.pce.provider.NeoDriver;
import org.bitbucket.openkilda.pce.provider.PathComputer;

public final class DefaultParameters {
    private static final String host = firstNonNull(System.getProperty("kilda.host"), "localhost");
    private static final String mininetPort = firstNonNull(System.getProperty("kilda.mininet.port"), "38080");
    private static final String topologyPort = firstNonNull(System.getProperty("kilda.topology.port"), "80");
    private static final String northboundPort = firstNonNull(System.getProperty("kilda.northbound.port"), "8088");
    private static final String opentsdbPort = firstNonNull(System.getProperty("kilda.opentsdb.port"), "4242");
    public static final String topologyUsername = firstNonNull(System.getProperty("kilda.topology.username"), "kilda");
    public static final String topologyPassword = firstNonNull(System.getProperty("kilda.topology.password"), "kilda");
    public static final String mininetEndpoint = String.format("http://%s:%s", host, mininetPort);
    public static final String trafficEndpoint = String.format("http://%s:%s", host, "17191");
    public static final String topologyEndpoint = String.format("http://%s:%s", host, topologyPort);
    public static final String northboundEndpoint = String.format("http://%s:%s", host, northboundPort);
    public static final String opentsdbEndpoint = String.format("http://%s:%s", host, opentsdbPort);
    public static final PathComputer pathComputer = new NeoDriver(host, "neo4j", "temppass");

    static {
        System.out.println(String.format("Mininet Endpoint: %s", mininetEndpoint));
        System.out.println(String.format("Topology Endpoint: %s", topologyEndpoint));
        System.out.println(String.format("Northbound Endpoint: %s", northboundEndpoint));
        System.out.println(String.format("OpenTSDB Endpoint: %s", opentsdbEndpoint));
    }

    private DefaultParameters() {
    }
}
