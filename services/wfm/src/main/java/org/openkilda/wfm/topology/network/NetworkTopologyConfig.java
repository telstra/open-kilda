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

package org.openkilda.wfm.topology.network;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.IgnoreKey;
import com.sabre.oss.conf4j.annotation.Key;

public interface NetworkTopologyConfig extends AbstractTopologyConfig {
    @IgnoreKey
    DiscoveryConfig getDiscoveryConfig();

    default int getScaleFactor() {
        return getDiscoveryConfig().getScaleFactor();
    }

    default int getDiscoveryInterval() {
        return getDiscoveryConfig().getDiscoveryInterval();
    }

    default int getDiscoveryPacketTtl() {
        return getDiscoveryConfig().getDiscoveryPacketTtl();
    }

    default int getDiscoveryTimeout() {
        return getDiscoveryConfig().getDiscoveryTimeout();
    }

    default boolean isBfdEnabled() {
        return getDiscoveryConfig().isBfdEnabled();
    }

    default String getTopoDiscoTopic() {
        return getKafkaTopics().getTopoDiscoTopic();
    }

    default String getKafkaSpeakerDiscoTopic() {
        return getKafkaTopics().getSpeakerDiscoTopic();
    }

    default String getKafkaTopoRerouteTopic() {
        return getKafkaTopics().getTopoRerouteTopic();
    }

    @Key("bfd.port.offset")
    @Default("200")
    int getBfdPortOffset();

    @Key("isl.cost.when.port.down")
    int getIslCostWhenPortDown();

    @Key("isl.cost.when.under.maintenance")
    int getIslCostWhenUnderMaintenance();

    @Key("speaker.io.timeout.seconds")
    @Default("60")
    int getSpeakerIoTimeoutSeconds();

    @Key("port.up.down.throttling.delay.seconds.min")
    int getPortUpDownThrottlingDelaySecondsMin();

    @Key("port.up.down.throttling.delay.seconds.warm.up")
    int getPortUpDownThrottlingDelaySecondsWarmUp();

    @Key("port.up.down.throttling.delay.seconds.cool.down")
    int getPortUpDownThrottlingDelaySecondsCoolDown();

    @Configuration
    @Key("discovery")
    interface DiscoveryConfig {
        @Key("scale-factor")
        @Default("2")
        int getScaleFactor();

        @Key("interval")
        int getDiscoveryInterval();

        @Key("packet.ttl")
        int getDiscoveryPacketTtl();

        @Key("timeout")
        int getDiscoveryTimeout();

        @Key("use-bfd")
        boolean isBfdEnabled();
    }
}
