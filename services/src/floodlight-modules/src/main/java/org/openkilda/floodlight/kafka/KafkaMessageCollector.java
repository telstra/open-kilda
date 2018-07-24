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
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.switchmanager.ISwitchManager;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KafkaMessageCollector implements IFloodlightModule {
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
    public void init(FloodlightModuleContext context) {
    }

    @Override
    public void startUp(FloodlightModuleContext moduleContext) {
        ConfigurationProvider provider = new ConfigurationProvider(moduleContext, this);
        KafkaConsumerConfig consumerConfig = provider.getConfiguration(KafkaConsumerConfig.class);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);

        // A thread pool of fixed sized and no work queue.
        ExecutorService parseRecordExecutor = new ThreadPoolExecutor(consumerConfig.getExecutorCount(),
                consumerConfig.getExecutorCount(), 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());

        ConsumerContext context = new ConsumerContext(moduleContext, topicsConfig);
        RecordHandler.Factory handlerFactory = new RecordHandler.Factory(context);

        ISwitchManager switchManager = context.getSwitchManager();
        String inputTopic = topicsConfig.getSpeakerTopic();

        logger.info("Starting {}", this.getClass().getCanonicalName());
        try {

            Consumer consumer;
            if (!consumerConfig.isTestingMode()) {
                consumer = new Consumer(consumerConfig, parseRecordExecutor, handlerFactory, switchManager,
                        inputTopic);
            } else {
                consumer = new TestAwareConsumer(context,
                        consumerConfig, parseRecordExecutor, handlerFactory, switchManager, inputTopic);
            }
            Executors.newSingleThreadExecutor().execute(consumer);
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }
}
