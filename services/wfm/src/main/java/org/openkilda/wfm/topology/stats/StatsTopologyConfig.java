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

package org.openkilda.wfm.topology.stats;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;

@Configuration
public interface StatsTopologyConfig extends AbstractTopologyConfig {

    default String getKafkaOtsdbTopic() {
        return getKafkaTopics().getOtsdbTopic();
    }

    default String getKafkaStatsTopic() {
        return getKafkaTopics().getStatsTopic();
    }

    default String getKafkaSpeakerFlowTopic() {
        return getKafkaTopics().getSpeakerFlowTopic();
    }
}
