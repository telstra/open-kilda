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

import static java.util.Objects.requireNonNull;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.Collection;

public class ConsumerContext extends Context {
    private final IPathVerificationService pathVerificationService;
    private final KafkaMessageProducer kafkaProducer;
    private final ISwitchManager switchManager;
    private final KafkaTopicsConfig kafkaTopicsConfig;

    public static void fillDependencies(Collection<Class<? extends IFloodlightService>> dependencies) {
        dependencies.add(IPathVerificationService.class);
        dependencies.add(ISwitchManager.class);
    }

    public ConsumerContext(FloodlightModuleContext moduleContext, KafkaTopicsConfig kafkaTopicsConfig) {
        super(moduleContext);

        pathVerificationService = moduleContext.getServiceImpl(IPathVerificationService.class);
        switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        kafkaProducer = moduleContext.getServiceImpl(KafkaMessageProducer.class);

        this.kafkaTopicsConfig = requireNonNull(kafkaTopicsConfig, "kafkaTopicsConfig cannot be null");
    }

    public IPathVerificationService getPathVerificationService() {
        return pathVerificationService;
    }

    public KafkaMessageProducer getKafkaProducer() {
        return kafkaProducer;
    }

    public ISwitchManager getSwitchManager() {
        return switchManager;
    }

    public String getKafkaFlowTopic() {
        return kafkaTopicsConfig.getFlowTopic();
    }

    public String getKafkaTopoDiscoTopic() {
        return kafkaTopicsConfig.getTopoDiscoTopic();
    }

    public String getKafkaStatsTopic() {
        return kafkaTopicsConfig.getStatsTopic();
    }

    public String getKafkaNorthboundTopic() {
        return kafkaTopicsConfig.getNorthboundTopic();
    }
}
