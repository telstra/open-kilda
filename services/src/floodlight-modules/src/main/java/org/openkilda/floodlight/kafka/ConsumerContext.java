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

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class ConsumerContext {
    private final FloodlightModuleContext moduleContext;
    private final IPathVerificationService pathVerificationService;
    private final ISwitchManager switchManager;

    private final KafkaTopicsConfig kafkaTopics;

    public ConsumerContext(FloodlightModuleContext moduleContext) {
        this.moduleContext = moduleContext;
        this.pathVerificationService = moduleContext.getServiceImpl(IPathVerificationService.class);
        this.switchManager = moduleContext.getServiceImpl(ISwitchManager.class);

        kafkaTopics = moduleContext.getServiceImpl(KafkaUtilityService.class).getTopics();
    }

    public FloodlightModuleContext getModuleContext() {
        return moduleContext;
    }

    public IPathVerificationService getPathVerificationService() {
        return pathVerificationService;
    }

    public ISwitchManager getSwitchManager() {
        return switchManager;
    }

    public String getKafkaFlowTopic() {
        return kafkaTopics.getFlowTopic();
    }

    public String getKafkaTopoDiscoTopic() {
        return kafkaTopics.getTopoDiscoTopic();
    }

    public String getKafkaStatsTopic() {
        return kafkaTopics.getStatsTopic();
    }

    public String getKafkaNorthboundTopic() {
        return kafkaTopics.getNorthboundTopic();
    }

    public String getKafkaTopoEngTopic() {
        return kafkaTopics.getTopoEngTopic();
    }
}
