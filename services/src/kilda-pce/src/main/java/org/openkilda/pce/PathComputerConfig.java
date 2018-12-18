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

    @Key("diversity.isl.weight")
    @Default("1000")
    int getDiversityIslWeight();

    @Key("diversity.switch.weight")
    @Default("100")
    int getDiversitySwitchWeight();

    @Key("strategy")
    @Default("COST")
    String getStrategy();

    @Key("network.strategy")
    @Default("COST")
    String getNetworkStrategy();
}
