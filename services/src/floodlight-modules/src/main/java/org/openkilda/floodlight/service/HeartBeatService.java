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

package org.openkilda.floodlight.service;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.config.UnitConverter;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HeartBeatService implements IService {
    private final KafkaChannel owner;

    private IKafkaProducerService producerService;
    private ScheduledExecutorService scheduler;
    private long interval;
    private String topic;

    private ScheduledFuture<?> currentTask;

    public HeartBeatService(KafkaChannel owner) {
        this.owner = owner;
    }

    @Override
    public void setup(FloodlightModuleContext moduleContext) {
        producerService = moduleContext.getServiceImpl(IKafkaProducerService.class);
        scheduler = moduleContext.getServiceImpl(IThreadPoolService.class).getScheduledExecutor();

        interval = UnitConverter.timeMillis(owner.getConfig().getHeartBeatInterval());
        topic = owner.getTopoDiscoTopic();

        currentTask = scheduler.scheduleAtFixedRate(new Action(this), interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Postpone execution - restart wait cycle from zero.
     */
    public void reschedule() {
        TimerTask replace = new Action(this);
        ScheduledFuture<?> timer = scheduler.scheduleAtFixedRate(replace, interval, interval, TimeUnit.MILLISECONDS);

        synchronized (this) {
            currentTask.cancel(false);
            currentTask = timer;
        }
    }

    @NewCorrelationContextRequired
    private void timerAction() {
        Message message = new org.openkilda.messaging.HeartBeat(System.currentTimeMillis(), CorrelationContext.getId());
        producerService.sendMessageAndTrack(topic, message);
    }

    private static class Action extends TimerTask {
        private final HeartBeatService service;

        Action(HeartBeatService service) {
            this.service = service;
        }

        @Override
        public void run() {
            service.timerAction();
        }
    }
}
