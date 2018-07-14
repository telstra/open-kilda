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

package org.openkilda.wfm.topology.packetmon;

import org.openkilda.wfm.config.SecondsToMilisConverter;
import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Converter;
import com.sabre.oss.conf4j.annotation.IgnoreKey;
import com.sabre.oss.conf4j.annotation.Key;

public interface PacketMonTopologyConfig extends AbstractTopologyConfig {
    @IgnoreKey
    PacketmonConfig getPacketmonTopologyConfig();

    default String getKafkaOtsdbTopic() {
        return getKafkaTopics().getOtsdbTopic();
    }

    @Configuration
    @Key("packetmon")
    interface PacketmonConfig {
        @Key("timeout")
        @Converter(SecondsToMilisConverter.class)
        int getTimeout();

        @Key("num.spouts")
        int getNumSpouts();

        @Key("flowmonbolt.cachetimeout.seconds")
        int getFlowmonBoltCacheTimeout();

        @Key("flowmonbolt.windowsize")
        int getWindowsize();

        @Key("flowmonbolt.maxtimedelta")
        int getMaxTimeDelta();

        @Key("flowmonbolt.maxvariance")
        int getMaxVariance();

        @Key("flowmonbolt.executors")
        int getFlowMonBoltExecutors();

        @Key("flowmonbolt.workers")
        int getFlowMonBoltWorkers();

        @Key("badflowbolt.cachetimeout.seconds")
        int getBadFlowBoltCacheTimeout();

        @Key("badflowbolt.executors")
        int getBadFlowBoltExecutors();

        @Key("badflowbolt.workers")
        int getBadFlowBoltWorkers();

        @Key("tsdbbolt.executors")
        int getTsdbBoltExecutors();

        @Key("tsdbbolt.workers")
        int getTsdbBoltWorkers();
    }
}
