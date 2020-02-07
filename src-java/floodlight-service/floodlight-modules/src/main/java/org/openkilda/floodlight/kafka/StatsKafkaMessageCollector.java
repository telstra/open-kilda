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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaConsumerSetup;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchTrackingService;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ExecutorService;

public class StatsKafkaMessageCollector extends KafkaMessageCollector {
    private static final Logger logger = LoggerFactory.getLogger(StatsKafkaMessageCollector.class);

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(
                ISwitchManager.class,
                KafkaUtilityService.class,
                IKafkaProducerService.class,
                CommandProcessorService.class,
                SwitchTrackingService.class,
                SessionService.class);
    }

    @Override
    protected void launchTopics(KafkaMessageCollectorConfig consumerConfig,
                                KafkaChannel kafkaChannel,
                                ConsumerLauncher launcher) {
        ExecutorService generalExecutor = buildExecutorWithNoQueue(consumerConfig.getGeneralExecutorCount());
        logger.info("Kafka Consumer: general executor threads = {}", consumerConfig.getGeneralExecutorCount());
        launcher.launch(generalExecutor, new KafkaConsumerSetup(kafkaChannel.getStatsStatsRequetstPrivRegionTopic()));
    }
}
