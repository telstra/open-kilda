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

import javax.validation.constraints.Min;

public interface SwitchManagerTopologyConfig  extends AbstractTopologyConfig {

    default String getKafkaSwitchManagerTopic() {
        return getKafkaTopics().getTopoSwitchManagerTopic();
    }

    default String getKafkaNorthboundTopic() {
        return getKafkaTopics().getNorthboundTopic();
    }

    default String getKafkaSpeakerTopic() {
        return getKafkaTopics().getSpeakerTopic();
    }

    @Key("burst.coefficient")
    @Default("1.05")
    @Description("This coefficient is used to calculate burst size for flow meters. "
            + "Burst size will be equal to 'coefficient * bandwidth'. "
            + "Value '0.1' means that burst size will be equal to 10% of flow bandwidth.")
    double getFlowMeterBurstCoefficient();

    @Key("min.burst.size.in.kbits")
    @Default("1024")
    @Min(0)
    @Description("Minimum possible burst size for flow meters. "
            + "It will be used instead of calculated flow meter burst size "
            + "if calculated value will be less than value of this option.")
    long getFlowMeterMinBurstSizeInKbits();

    @Key("swmanager.operation.timeout")
    @Default("10000")
    @Description("The timeout for performing async operations")
    int getOperationTimeout();
}
