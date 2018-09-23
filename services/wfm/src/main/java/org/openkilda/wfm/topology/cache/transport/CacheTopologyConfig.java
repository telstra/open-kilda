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

package org.openkilda.wfm.topology.cache.transport;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;

@Configuration
public interface CacheTopologyConfig extends AbstractTopologyConfig {

    @Key("reroute.throttling.delay.min")
    long getRerouteThrottlingMinDelay();

    @Key("reroute.throttling.delay.max")
    long getRerouteThrottlingMaxDelay();

    default String getKafkaFlowTopic() {
        return getKafkaTopics().getFlowTopic();
    }

    default String getKafkaTopoEngTopic() {
        return getKafkaTopics().getTopoEngTopic();
    }

    default String getKafkaTopoCacheTopic() {
        return getKafkaTopics().getTopoCacheTopic();
    }
}
