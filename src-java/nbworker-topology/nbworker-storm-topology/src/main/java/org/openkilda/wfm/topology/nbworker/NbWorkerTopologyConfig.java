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

package org.openkilda.wfm.topology.nbworker;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Description;
import com.sabre.oss.conf4j.annotation.Key;

import javax.validation.constraints.Min;

@Configuration
public interface NbWorkerTopologyConfig extends AbstractTopologyConfig {

    default String getKafkaTopoNbTopic() {
        return getKafkaTopics().getTopoNbTopic();
    }

    default String getKafkaNorthboundTopic() {
        return getKafkaTopics().getNorthboundTopic();
    }

    default String getKafkaFlowHsTopic() {
        return getKafkaTopics().getFlowHsTopic();
    }

    default String getKafkaDiscoTopic() {
        return getKafkaTopics().getTopoDiscoTopic();
    }

    default String getKafkaPingTopic() {
        return getKafkaTopics().getPingTopic();
    }

    default String getKafkaSpeakerTopic() {
        return getKafkaTopics().getSpeakerTopic();
    }

    default String getKafkaSwitchManagerTopic() {
        return  getKafkaTopics().getTopoSwitchManagerNbWorkerTopic();
    }

    default String getKafkaServer42StormNotifyTopic() {
        return getKafkaTopics().getNbWorkerServer42StormNotifyTopic();
    }

    default String getKafkaRerouteTopic() {
        return  getKafkaTopics().getTopoRerouteTopic();
    }

    @Key("nbworker.operation.timeout.seconds")
    @Default("10")
    @Description("The timeout for performing async operations")
    int getOperationTimeout();

    @Key("nbworker.process.timeout.seconds")
    @Default("20")
    @Description("The timeout for performing H&S operations")
    int getProcessTimeout();

    @Key("burst.coefficient")
    @Default("1.05")
    double getFlowMeterBurstCoefficient();

    @Key("min.burst.size.in.kbits")
    @Default("1024")
    @Min(0)
    long getFlowMeterMinBurstSizeInKbits();
}
