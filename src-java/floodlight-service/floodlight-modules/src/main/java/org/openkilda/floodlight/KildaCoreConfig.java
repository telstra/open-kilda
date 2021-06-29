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

package org.openkilda.floodlight;

import org.openkilda.config.converter.EnumLowerCaseConverter;
import org.openkilda.floodlight.model.FloodlightRole;

import com.sabre.oss.conf4j.annotation.Converter;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

public interface KildaCoreConfig {
    @Key("command-processor-workers-count")
    @Default("4")
    int getCommandPersistentWorkersCount();

    @Key("command-processor-workers-limit")
    @Default("32")
    int getCommandWorkersLimit();

    @Key("command-processor-deferred-requests-limit")
    @Default("8")
    int getCommandDeferredRequestsLimit();

    @Key("command-processor-idle-workers-keep-alive-seconds")
    @Default("300")
    long getCommandIdleWorkersKeepAliveSeconds();

    @Key("flow-ping-magic-src-mac-address")
    @Default("00:26:E1:FF:FF:FE")
    String getFlowPingMagicSrcMacAddress();

    /**
     * This offset is used for encoding flow in_port number into udp_src port of Server 42 Flow RTT packets.
     * Example: Flow with in_port 10. Server 42 Input rule will match RTT packets by
     * udp_src port number 5010 (offset + flow in_port).
     * We need an offset to do not intersect with some popular ports (like 22 port for ssh)
     */
    @Key("server42-flow-rtt-udp-port-offset")
    @Default("5000")
    int getServer42FlowRttUdpPortOffset();

    @Key("role")
    @Default("management")
    @Converter(EnumLowerCaseConverter.class)
    FloodlightRole getRole();

    /**
     * This offset is used for encoding ISL in_port number into udp_src port of Server 42 ISL RTT packets.
     */
    @Key("server42-isl-rtt-udp-port-offset")
    @Default("10000")
    int getServer42IslRttUdpPortOffset();

    @Key("server42-isl-rtt-magic-mac-address")
    @Default("00:26:E1:FF:FF:FD")
    String getServer42IslRttMagicMacAddress();

}
