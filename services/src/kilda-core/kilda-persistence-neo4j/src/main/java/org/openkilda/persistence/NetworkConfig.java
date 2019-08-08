/* Copyright 2019 Telstra Open Source
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

package org.openkilda.persistence;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import java.io.Serializable;

@Configuration
public interface NetworkConfig extends Serializable {

    @Key("isl.unstable.timeout.sec")
    @Default("7200")
    int getIslUnstableTimeoutSec();

    @Key("isl.cost.when.port.down")
    @Default("10000")
    int getIslCostWhenPortDown();

    @Key("isl.cost.when.under.maintenance")
    @Default("10000")
    int getIslCostWhenUnderMaintenance();
}
