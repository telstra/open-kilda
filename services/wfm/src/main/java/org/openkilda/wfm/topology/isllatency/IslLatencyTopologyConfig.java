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

package org.openkilda.wfm.topology.isllatency;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import javax.validation.constraints.Min;

@Configuration
public interface IslLatencyTopologyConfig extends AbstractTopologyConfig {

    default String getKafkaTopoIslLatencyTopic() {
        return getKafkaTopics().getTopoIslLatencyTopic();
    }

    default String getKafkaTopoIslStatusTopic() {
        return getKafkaTopics().getTopoIslStatusTopic();
    }

    default String getKafkaOtsdbTopic() {
        return getKafkaTopics().getOtsdbTopic();
    }

    @Key("opentsdb.metric.prefix")
    @Default("kilda.")
    String getMetricPrefix();

    @Key("latency.update.interval")
    @Default("600")
    @Min(1)
    int getLatencyUpdateInterval();

    @Key("latency.update.time.range")
    @Default("300")
    @Min(1)
    int getLatencyUpdateTimeRange();

    @Key("discovery.interval.multiplier")
    @Default("3")
    @Min(1)
    double getDiscoveryIntervalMultiplier();

    @Key("discovery.interval")
    int getDiscoveryInterval();
}
