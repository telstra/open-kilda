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
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.ConfigService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.utils.CommandContextFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KafkaMessageCollector implements IFloodlightModule {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageCollector.class);

    private final CommandContextFactory commandContextFactory = new CommandContextFactory();

    private final CommandProcessorService commandProcessor;
    private final InputService inputService;
    private final ConfigService configService = new ConfigService();
    private final PingService pingService = new PingService();

    public KafkaMessageCollector() {
        commandProcessor = new CommandProcessorService(commandContextFactory);
        inputService = new InputService(commandContextFactory);
    }

    /**
     * IFloodLightModule Methods.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(
                ConfigService.class,
                CommandProcessorService.class,
                InputService.class,
                PingService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(
                ConfigService.class, configService,
                CommandProcessorService.class, commandProcessor,
                InputService.class, inputService,
                PingService.class, pingService);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> dependencies = new ArrayList<>();
        ConsumerContext.fillDependencies(dependencies);

        dependencies.add(IFloodlightProviderService.class);
        dependencies.add(IOFSwitchService.class);
        dependencies.add(IThreadPoolService.class);
        dependencies.add(KafkaMessageProducer.class);

        return dependencies;
    }

    @Override
    public void init(FloodlightModuleContext moduleContext) {
        configService.init(ConfigurationProvider.of(moduleContext, this));
        commandContextFactory.init(moduleContext);
        inputService.init(moduleContext);
    }

    @Override
    public void startUp(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        logger.info("Starting {}", this.getClass().getCanonicalName());

        commandProcessor.init(moduleContext);
        pingService.init(moduleContext);

        KafkaConsumerConfig consumerConfig = configService.getConsumerConfig();
        KafkaTopicsConfig topicsConfig = configService.getTopics();
        ConsumerContext context = new ConsumerContext(moduleContext, topicsConfig);

        ExecutorService generalExecutor = buildExecutorWithNoQueue(consumerConfig.getGeneralExecutorCount());
        logger.info("Kafka Consumer: general executor threads = {}", consumerConfig.getGeneralExecutorCount());

        initConsumer(consumerConfig, context, topicsConfig.getSpeakerTopic(), generalExecutor);
        initConsumer(consumerConfig, context, topicsConfig.getSpeakerFlowTopic(), generalExecutor);
        initConsumer(consumerConfig, context, topicsConfig.getSpeakerFlowPingTopic(), generalExecutor);

        ExecutorService discoCommandExecutor = buildExecutorWithNoQueue(consumerConfig.getDiscoExecutorCount());
        logger.info("Kafka Consumer: disco executor threads = {}", consumerConfig.getDiscoExecutorCount());

        initConsumer(consumerConfig, context, topicsConfig.getSpeakerDiscoTopic(), discoCommandExecutor,
                OffsetResetStrategy.LATEST);
    }

    private ExecutorService buildExecutorWithNoQueue(int executorCount) {
        // A thread pool of fixed sized and no work queue.
        return new ThreadPoolExecutor(executorCount, executorCount, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), new RetryableExecutionHandler());
    }

    private void initConsumer(KafkaConsumerConfig consumerConfig, ConsumerContext context, String topic,
                              ExecutorService handlerExecutor) {
        initConsumer(consumerConfig, context, topic, handlerExecutor, null);
    }

    private void initConsumer(KafkaConsumerConfig consumerConfig, ConsumerContext context, String topic,
                              ExecutorService handlerExecutor, OffsetResetStrategy defaultOffsetStrategy) {
        logger.info("Kafka Consumer: topic = {}", topic);

        RecordHandler.Factory handlerFactory = new RecordHandler.Factory(context);
        ISwitchManager switchManager = context.getSwitchManager();

        try {
            Consumer consumer;
            if (!consumerConfig.isTestingMode()) {
                consumer = new Consumer(consumerConfig, handlerExecutor, handlerFactory,
                        switchManager, topic, defaultOffsetStrategy);
            } else {
                consumer = new TestAwareConsumer(context, consumerConfig, handlerExecutor, handlerFactory,
                        switchManager, topic, defaultOffsetStrategy);
            }
            Executors.newSingleThreadScheduledExecutor()
                    .scheduleWithFixedDelay(consumer, 0, 1, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            logger.error("error", exception);
        }
    }

    /**
     * Handler of rejected messages by ThreadPoolExecutor, in case of reject this handler will wait
     * until one of executors becomes available.
     */
    private class RetryableExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    logger.error("Couldn't retry to process message", e);
                }
            }
        }
    }
}
