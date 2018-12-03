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

package org.openkilda.floodlight.switchmanager;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Description;
import com.sabre.oss.conf4j.annotation.Key;

import javax.validation.constraints.Min;

@Configuration
public interface SwitchManagerConfig {
    @Key("connect-mode")
    String getConnectMode();

    @Key("broadcast-rate-limit")
    int getBroadcastRateLimit();

    @Key("unicast-rate-limit")
    int getUnicastRateLimit();

    @Key("disco-packet-size")
    int getDiscoPacketSize();

    @Key("flow-meter-burst-coefficient")
    @Default("0.1")
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

    @Key("ovs-meters-enabled")
    boolean getOvsMetersEnabled();
}
