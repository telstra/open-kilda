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

package org.openkilda.wfm.config;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;

@Configuration
@Key("kafka")
public interface KafkaConfig {
    // To be able to run tests in parallel we must use different ports for kafka hosts in each test
    int STATS_TOPOLOGY_TEST_KAFKA_PORT = 2188;
    int ISL_LATENCY_TOPOLOGY_TEST_KAFKA_PORT = 2189;
    int FLOW_PING_TOPOLOGY_TEST_KAFKA_PORT = 2190;

    @Key("hosts")
    String getHosts();

    @Key("listeners")
    String getListeners();

    @Key("advertised.listeners")
    String getAdvertisedListeners();
}
