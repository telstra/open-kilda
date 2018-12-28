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

package org.openkilda;

import static com.google.common.base.MoreObjects.firstNonNull;

public final class DefaultParameters {
    private static final String host = firstNonNull(System.getProperty("kilda.host"), "localhost");
    private static final String mininetPort = firstNonNull(System.getProperty("kilda.mininet.port"), "38080");
    private static final String topologyPort = firstNonNull(System.getProperty("kilda.topology.port"), "80");
    private static final String northboundPort = firstNonNull(System.getProperty("kilda.northbound.port"), "8080");
    private static final String opentsdbPort = firstNonNull(System.getProperty("kilda.opentsdb.port"), "4242");
    private static final String FLOODLIGHT_PORT = firstNonNull(System.getProperty("kilda.floodlight.port"), "8081");
    public static final String topologyUsername = firstNonNull(System.getProperty("kilda.topology.username"), "kilda");
    public static final String topologyPassword = firstNonNull(System.getProperty("kilda.topology.password"), "kilda");
    public static final String mininetEndpoint = String.format("http://%s:%s", host, mininetPort);
    public static final String trafficEndpoint = String.format("http://%s:%s", host, "17191");
    public static final String topologyEndpoint = String.format("http://%s:%s", host, topologyPort);
    public static final String northboundEndpoint = String.format("http://%s:%s", host, northboundPort);
    public static final String opentsdbEndpoint = String.format("http://%s:%s", host, opentsdbPort);
    public static final String FLOODLIGHT_ENDPOINT = String.format("http://%s:%s", host, FLOODLIGHT_PORT);

    static {
        System.out.println(String.format("Mininet Endpoint: %s", mininetEndpoint));
        System.out.println(String.format("Topology Endpoint: %s", topologyEndpoint));
        System.out.println(String.format("Northbound Endpoint: %s", northboundEndpoint));
        System.out.println(String.format("OpenTSDB Endpoint: %s", opentsdbEndpoint));
    }

    private DefaultParameters() {
    }
}
