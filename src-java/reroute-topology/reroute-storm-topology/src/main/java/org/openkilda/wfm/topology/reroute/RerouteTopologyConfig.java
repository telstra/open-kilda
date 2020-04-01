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

package org.openkilda.wfm.topology.reroute;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

@Configuration
public interface RerouteTopologyConfig extends AbstractTopologyConfig {

    @Key("reroute.throttling.delay.min")
    long getRerouteThrottlingMinDelay();

    @Key("reroute.throttling.delay.max")
    long getRerouteThrottlingMaxDelay();

    @Key("flow.default.priority")
    @Default("1000")
    int getDefaultFlowPriority();

    @Key("flow.max.retry.counter")
    @Default("3")
    int getMaxRetry();

    @Key("reroute.timeout.seconds")
    @Default("80")
    int getRerouteTimeoutSeconds();

    default String getKafkaTopoRerouteTopic() {
        return getKafkaTopics().getTopoRerouteTopic();
    }

    default String getKafkaFlowTopic() {
        return getKafkaTopics().getFlowTopic();
    }

    default String getKafkaFlowHsTopic() {
        return getKafkaTopics().getFlowHsTopic();
    }

    default String getKafkaNorthboundTopic() {
        return getKafkaTopics().getNorthboundTopic();
    }
}
