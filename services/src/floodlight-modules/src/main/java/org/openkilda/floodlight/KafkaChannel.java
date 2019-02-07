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

package org.openkilda.floodlight;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaProducerProxy;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.Collection;
import java.util.Map;

public class KafkaChannel implements IFloodlightModule {
    private KafkaChannelConfig config;
    private KafkaTopicsConfig topics;

    public KafkaChannelConfig getConfig() {
        return config;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(
                KafkaUtilityService.class,
                IKafkaProducerService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(
                KafkaUtilityService.class, new KafkaUtilityService(this),
                IKafkaProducerService.class, new KafkaProducerProxy(this));
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of();
    }

    @Override
    public void init(FloodlightModuleContext moduleContext) {
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(moduleContext, this);
        config = provider.getConfiguration(KafkaChannelConfig.class);
        topics = provider.getConfiguration(KafkaTopicsConfig.class);
    }

    @Override
    public void startUp(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        moduleContext.getServiceImpl(KafkaUtilityService.class).setup(moduleContext);
        moduleContext.getServiceImpl(IKafkaProducerService.class).setup(moduleContext);
    }

    public String getRegion() {
        return config.getFloodlightRegion();
    }

    public String getSpeakerTopic() {
        return formatTopicWithRegion(topics.getSpeakerTopic());
    }

    public String getSpeakerFlowTopic() {
        return formatTopicWithRegion(topics.getSpeakerFlowTopic());
    }

    public String getSpeakerFlowPingTopic() {
        return formatTopicWithRegion(topics.getSpeakerFlowPingTopic());
    }

    public String getSpeakerDiscoTopic() {
        return formatTopicWithRegion(topics.getSpeakerDiscoTopic());
    }

    public String getStatsTopic() {
        return topics.getStatsTopic();
    }

    public String getFlowTopic() {
        return formatTopicWithRegion(topics.getFlowTopic());
    }

    public String getTopoDiscoTopic() {
        return formatTopicWithRegion(topics.getTopoDiscoTopic());
    }

    public String getNorthboundTopic() {
        return topics.getNorthboundTopic();
    }

    public String getKafkaNbWorkerTopic() {
        return topics.getTopoNbTopic();
    }

    public String  getPingTopic() {
        return topics.getPingTopic();
    }

    private String formatTopicWithRegion(String topic) {
        String region =  config.getFloodlightRegion();
        if (region == null) {
            return topic;
        }
        return String.format("%s_%s", topic, region);
    }

    public String getTopoNbTopic() {
        return topics.getTopoNbTopic();
    }

    public String getTopoSwitchManagerTopic() {
        return topics.getTopoSwitchManagerTopic();
    }
}
