/* Copyright 2017 Telstra Open Source
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
import org.openkilda.floodlight.config.KafkaFloodlightConfig;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KafkaMessageCollector implements IFloodlightModule {
    private static int EXEC_POOL_SIZE = 10;

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageCollector.class);

    /**
     * IFloodLightModule Methods
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>();
        ConsumerContext.fillDependencies(services);
        return services;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    }

    @Override
    public void startUp(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        ConfigurationProvider provider = new ConfigurationProvider(moduleContext, this);

        KafkaFloodlightConfig kafkaConfig = provider.getConfiguration(KafkaFloodlightConfig.class);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);
        String inputTopic = topicsConfig.getSpeakerTopic();

        ConsumerContext context = new ConsumerContext(moduleContext, kafkaConfig, topicsConfig);
        RecordHandler.Factory handlerFactory = new RecordHandler.Factory(context);
        ISwitchManager switchManager = moduleContext.getServiceImpl(ISwitchManager.class);

        logger.info("Starting {}", this.getClass().getCanonicalName());
        try {
            ExecutorService parseRecordExecutor = Executors.newFixedThreadPool(EXEC_POOL_SIZE);

            Consumer consumer;
            if (!context.isTestingMode()) {
                consumer = new Consumer(context, parseRecordExecutor, handlerFactory, switchManager, inputTopic);
            } else {
                consumer = new TestAwareConsumer(context, parseRecordExecutor, handlerFactory, switchManager, inputTopic);
            }
            Executors.newSingleThreadExecutor().execute(consumer);
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }
}
