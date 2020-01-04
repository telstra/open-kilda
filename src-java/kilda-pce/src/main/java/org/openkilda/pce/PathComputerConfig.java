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

package org.openkilda.pce;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import java.io.Serializable;

@Configuration
@Key("pce")
public interface PathComputerConfig extends Serializable {
    @Key("max.allowed.depth")
    @Default("35")
    int getMaxAllowedDepth();

    @Key("default.isl.cost")
    @Default("700")
    int getDefaultIslCost();

    @Key("diversity.isl.cost")
    @Default("1000")
    int getDiversityIslCost();

    @Key("diversity.pop.isl.cost")
    @Default("1000")
    int getDiversityPopIslCost();

    @Key("diversity.switch.cost")
    @Default("100")
    int getDiversitySwitchCost();

    @Key("network.strategy")
    @Default("COST")
    String getNetworkStrategy();

    @Key("isl.cost.when.unstable")
    @Default("10000")
    int getUnstableCostRaise();

    @Key("isl.cost.when.under.maintenance")
    @Default("10000")
    int getUnderMaintenanceCostRaise();

    @Key("default.isl.latency")
    @Default("500000000")
    long getDefaultIslLatency();

    @Key("diversity.isl.latency")
    @Default("1000000000")
    long getDiversityIslLatency();

    @Key("diversity.switch.latency")
    @Default("300000000")
    long getDiversitySwitchLatency();

    @Key("isl.latency.when.unstable")
    @Default("10000000000")
    long getUnstableLatencyRaise();

    @Key("isl.latency.when.under.maintenance")
    @Default("10000000000")
    long getUnderMaintenanceLatencyRaise();
}
