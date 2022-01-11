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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.rulemanager.OfSpeakerService;
import org.openkilda.floodlight.kafka.discovery.NetworkDiscoveryEmitter;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import lombok.Getter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.time.Duration;

@Getter
public class ConsumerContext {
    private final FloodlightModuleContext moduleContext;
    private final ISwitchManager switchManager;
    private final KafkaChannel kafkaChannel;
    private final SpeakerCommandProcessor commandProcessor;
    private final NetworkDiscoveryEmitter discoveryEmitter;
    private final OfSpeakerService ofSpeakerService;

    public ConsumerContext(FloodlightModuleContext moduleContext, KafkaMessageCollectorConfig config) {
        this.moduleContext = moduleContext;

        this.switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        kafkaChannel = moduleContext.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        commandProcessor = new SpeakerCommandProcessor(moduleContext);

        Duration flushDelay = Duration.ofMillis(config.getDiscoveryFlushDelayMillis());
        discoveryEmitter = new NetworkDiscoveryEmitter(moduleContext, flushDelay);
        ofSpeakerService = new OfSpeakerService(moduleContext);
    }

    public String getRegion() {
        return kafkaChannel.getRegion();
    }

    public FloodlightModuleContext getModuleContext() {
        return moduleContext;
    }

    public ISwitchManager getSwitchManager() {
        return switchManager;
    }

    public SpeakerCommandProcessor getCommandProcessor() {
        return commandProcessor;
    }

    public OfSpeakerService getOfSpeakerService() {
        return ofSpeakerService;
    }

    public String getKafkaTopoDiscoTopic() {
        return kafkaChannel.getTopoDiscoTopic();
    }

    public String getKafkaStatsTopic() {
        return kafkaChannel.getStatsTopic();
    }

    public String getKafkaNorthboundTopic() {
        return kafkaChannel.getNorthboundTopic();
    }

    public String getKafkaNbWorkerTopic() {
        return kafkaChannel.getKafkaNbWorkerTopic();
    }

    public String getKafkaSwitchManagerTopic() {
        return kafkaChannel.getTopoSwitchManagerTopic();
    }

    public String getKafkaSpeakerFlowHsTopic() {
        return kafkaChannel.getSpeakerFlowHsTopic();
    }
}
