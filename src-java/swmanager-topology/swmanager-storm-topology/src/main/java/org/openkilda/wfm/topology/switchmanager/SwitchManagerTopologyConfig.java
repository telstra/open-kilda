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

package org.openkilda.wfm.topology.switchmanager;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Description;
import com.sabre.oss.conf4j.annotation.Key;

public interface SwitchManagerTopologyConfig  extends AbstractTopologyConfig {

    default String getKafkaSwitchManagerTopic() {
        return getKafkaTopics().getTopoSwitchManagerTopic();
    }

    default String getKafkaSwitchManagerNbTopic() {
        return getKafkaTopics().getTopoSwitchManagerNbTopic();
    }

    default String getKafkaSwitchManagerNetworkTopic() {
        return getKafkaTopics().getTopoSwitchManagerNetworkTopic();
    }

    default String getKafkaSwitchManagerNbWorkerTopic() {
        return getKafkaTopics().getTopoSwitchManagerNbWorkerTopic();
    }

    default String getKafkaNorthboundTopic() {
        return getKafkaTopics().getNorthboundTopic();
    }

    default String getKafkaSpeakerTopic() {
        return getKafkaTopics().getSpeakerTopic();
    }

    default String getKafkaSpeakerSwitchManagerTopic() {
        return getKafkaTopics().getSpeakerSwitchManagerTopic();
    }

    default String getKafkaSwitchManagerSpeakerWorkerTopic() {
        return getKafkaTopics().getSwitchManagerSpeakerTopic();
    }

    default String getGrpcSpeakerTopic() {
        return getKafkaTopics().getGrpcSpeakerTopic();
    }

    default String getGrpcResponseTopic() {
        return getKafkaTopics().getGrpcResponseTopic();
    }

    @Key("swmanager.operation.timeout.seconds")
    @Default("10")
    @Description("The timeout for performing async operations")
    int getOperationTimeout();

    @Key("swmanager.process.timeout.seconds")
    @Default("20")
    @Description("The timeout for performing validate and synchronize operations")
    int getProcessTimeout();

    @Key("lag.port.offset")
    @Default("2000")
    int getLagPortOffset();

    @Key("lag.port.max.number")
    @Default("2999")
    int getLagPortMaxNumber();

    @Key("lag.port.pool.chunks.count")
    @Default("10")
    int getLagPortPoolChunksCount();

    @Key("lag.port.pool.cache.size")
    @Default("128")
    int getLagPortPoolCacheSize();

    @Key("bfd.port.offset")
    @Default("1000")
    int getBfdPortOffset();

    @Key("bfd.port.max.number")
    @Default("1999")
    int getBfdPortMaxNumber();

    @Key("kafka.chunked.messages.expiration.minutes")
    @Default("15")
    int getChunkedMessagesExpirationMinutes();

    @Key("swmanager.of.commands.batch.size")
    @Default("500")
    int getOfCommandsBatchSize();
}
