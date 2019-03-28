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

package org.openkilda.wfm.topology.nbworker;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
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

    default String getKafkaFlowTopic() {
        return getKafkaTopics().getFlowTopic();
    }

    default String getKafkaFlowHsTopic() {
        return getKafkaTopics().getFlowHsTopic();
    }

    default String getKafkaDiscoTopic() {
        return getKafkaTopics().getTopoDiscoTopic();
    }

    default String getKafkaSpeakerTopic() {
        return getKafkaTopics().getSpeakerTopic();
    }

    @Key("isl.cost.when.under.maintenance")
    int getIslCostWhenUnderMaintenance();

    @Key("burst.coefficient")
    @Default("1.05")
    double getFlowMeterBurstCoefficient();

    @Key("min.burst.size.in.kbits")
    @Default("1024")
    @Min(0)
    long getFlowMeterMinBurstSizeInKbits();
}
