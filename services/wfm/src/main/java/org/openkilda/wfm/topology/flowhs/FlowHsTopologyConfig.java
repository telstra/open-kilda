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

package org.openkilda.wfm.topology.flowhs;

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

public interface FlowHsTopologyConfig extends AbstractTopologyConfig {
    default String getKafkaFlowHsTopic() {
        return getKafkaTopics().getFlowHsTopic();
    }

    default String getKafkaSpeakerFlowTopic() {
        return getKafkaTopics().getSpeakerFlowHsTopic();
    }

    default String getKafkaNorthboundTopic() {
        return getKafkaTopics().getNorthboundTopic();
    }

    default String getKafkaFlowSpeakerWorkerTopic() {
        return getKafkaTopics().getFlowHsSpeakerTopic();
    }

    @Key("flow.create.hub.timeout.seconds")
    @Default("30")
    int getCreateHubTimeoutSeconds();

    @Key("flow.create.speaker.timeout.seconds")
    @Default("10")
    int getCreateSpeakerTimeoutSeconds();

    @Key("flow.create.hub.retries")
    @Default("3")
    int getCreateHubRetries();

    @Key("flow.create.speaker.command.retries")
    @Default("3")
    int getCreateHubSpeakerCommandRetries();

    @Key("flow.reroute.hub.timeout.seconds")
    @Default("30")
    int getRerouteHubTimeoutSeconds();

    @Key("flow.reroute.speaker.timeout.seconds")
    @Default("10")
    int getRerouteSpeakerTimeoutSeconds();
}
