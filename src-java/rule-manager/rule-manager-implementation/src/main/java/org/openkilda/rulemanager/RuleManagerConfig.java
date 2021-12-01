/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Description;
import com.sabre.oss.conf4j.annotation.Key;

import java.io.Serializable;
import javax.validation.constraints.Min;

@Configuration
public interface RuleManagerConfig extends Serializable {

    @Key("discovery-bcast-packet-dst")
    @Default("00:26:E1:FF:FF:FF")
    String getDiscoveryBcastPacketDst();

    @Key("broadcast-rate-limit")
    @Default("200")
    int getBroadcastRateLimit();

    @Key("unicast-rate-limit")
    @Default("200")
    int getUnicastRateLimit();

    @Key("lldp-rate-limit")
    @Default("1")
    int getLldpRateLimit(); // rate in packets per second

    @Key("arp-rate-limit")
    @Default("1")
    int getArpRateLimit(); // rate in packets per second

    // Size of packets used for discovery. Used to calculate service meters rate in KBPS
    @Key("disco-packet-size")
    @Default("250")
    int getDiscoPacketSize();

    @Key("lldp-packet-size")
    @Default("300")
    int getLldpPacketSize(); // rate in bytes

    @Key("arp-packet-size")
    @Default("100")
    int getArpPacketSize(); // rate in bytes

    @Key("flow-meter-burst-coefficient")
    @Default("1.05")
    @Description("This coefficient is used to calculate burst size for flow meters. "
            + "Burst size will be equal to 'coefficient * bandwidth'. "
            + "Value '0.1' means that burst size will be equal to 10% of flow bandwidth.")
    double getFlowMeterBurstCoefficient();

    @Key("flow-meter-min-burst-size-in-kbits")
    @Default("1024")
    @Min(0)
    @Description("Minimum possible burst size for flow meters. "
            + "It will be used instead of calculated flow meter burst size "
            + "if calculated value will be less than value of this option.")
    long getFlowMeterMinBurstSizeInKbits();

    @Key("system-meter-burst-size-in-packets")
    @Default("4096")
    @Min(0)
    @Description("This is burst size for default rule meters in packets.")
    long getSystemMeterBurstSizeInPackets();

    @Key("lldp-meter-burst-size-in-packets")
    @Default("4096")
    @Min(0)
    @Description("This is burst size for LLDP rule meters in packets.")
    long getLldpMeterBurstSizeInPackets();

    @Key("arp-meter-burst-size-in-packets")
    @Default("4096")
    @Min(0)
    @Description("This is burst size for ARP rule meters in packets.")
    long getArpMeterBurstSizeInPackets();

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
}

