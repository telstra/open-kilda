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

package org.openkilda.wfm.topology.ping;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.IgnoreKey;
import com.sabre.oss.conf4j.annotation.Key;

@Configuration
public interface PingTopologyConfig extends AbstractTopologyConfig {
    @IgnoreKey
    PingConfig getPingConfig();

    default int getPingInterval() {
        return getPingConfig().getPingInterval();
    }

    default int getTimeout() {
        return getPingConfig().getTimeout();
    }

    default long getPeriodicPingCacheExpirationInterval() {
        return getPingConfig().getPeriodicPingCacheExpirySec();
    }

    default int getFailDelay() {
        return getPingConfig().getFailDelay();
    }

    default int getFailReset() {
        return getPingConfig().getFailReset();
    }

    default String getKafkaPingTopic() {
        return getKafkaTopics().getPingTopic();
    }

    default String getKafkaSpeakerFlowPingTopic() {
        return getKafkaTopics().getSpeakerFlowPingTopic();
    }

    default String getKafkaNorthboundTopic() {
        return getKafkaTopics().getNorthboundTopic();
    }

    default String getKafkaFlowStatusTopic() {
        return getKafkaTopics().getFlowStatusTopic();
    }

    default String getKafkaOtsdbTopic() {
        return getKafkaTopics().getOtsdbTopic();
    }

    @Key("opentsdb.metric.prefix")
    @Default("kilda.")
    String getMetricPrefix();

    @Configuration
    @Key("flow.ping")
    interface PingConfig {
        @Key("interval")
        @Default("1")
        int getPingInterval();

        @Key("timeout")
        @Default("10")
        int getTimeout();

        @Key("fail.delay")
        @Default("45")
        int getFailDelay();

        @Key("fail.reset")
        @Default("1800")
        int getFailReset();

        @Key("cache.expiry.sec")
        @Default("60")
        long getPeriodicPingCacheExpirySec();
    }
}
