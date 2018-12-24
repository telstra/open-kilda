/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter;

public final class ComponentType {

    public static final String ROUTER_SPEAKER_FLOW_KAFKA_SPOUT = "ROUTER_SPEAKER_FLOW_KAFKA_SPOUT";
    public static final String ROUTER_SPEAKER_KAFKA_SPOUT = "ROUTER_SPEAKER_KAFKA_SPOUT";
    public static final String SPEAKER_KAFKA_BOLT = "SPEAKER_KAFKA_BOLT";
    public static final String KILDA_FLOW_KAFKA_BOLT = "KILDA_FLOW_KAFKA_BOLT";
    public static final String KILDA_FLOW_KAFKA_SPOUT = "KILDA_FLOW_KAFKA_SPOUT";
    public static final String SPEAKER_FLOW_KAFKA_BOLT = "SPEAKER_FLOW_KAFKA_BOLT";
    public static final String SPEAKER_PING_KAFKA_SPOUT = "SPEAKER_PING_KAFKA_SPOUT";
    public static final String SPEAKER_PING_KAFKA_BOLT = "SPEAKER_PING_KAFKA_BOLT";
    public static final String KILDA_PING_KAFKA_SPOUT = "KILDA_PING_KAFKA_SPOUT";
    public static final String KILDA_PING_KAFKA_BOLT = "KILDA_PING_KAFKA_BOLT";
    public static final String SPEAKER_DISCO_KAFKA_SPOUT = "SPEAKER_DISCO_KAFKA_SPOUT";
    public static final String SPEAKER_DISCO_KAFKA_BOLT = "SPEAKER_DISCO_KAFKA_BOLT";
    public static final String ROUTER_TOPO_DISCO_SPOUT = "ROUTER_TOPO_DISCO_SPOUT";
    public static final String TOPO_DISCO_KAFKA_BOLT = "TOPO_DISCO_KAFKA_BOLT";
    public static final String ROUTER_BOLT = "ROUTER_BOLT";
    public static final String NORTHBOND_REPLY_KAFKA_BOLT = "NORTHBOND_REPLY_KAFKA_BOLT";

    private ComponentType() {}
}
