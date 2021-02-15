/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.service.zookeeper;

import org.openkilda.bluegreen.LifeCycleObserver;
import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.bluegreen.Signal;
import org.openkilda.bluegreen.ZkStateTracker;
import org.openkilda.bluegreen.ZkWatchDog;
import org.openkilda.bluegreen.ZkWriter;
import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import org.apache.zookeeper.KeeperException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class ZooKeeperService implements IService, LifeCycleObserver {

    public static final String ZK_COMPONENT_NAME = "floodlight";

    private final Set<ZooKeeperEventObserver> observers = new HashSet<>();

    private ZkStateTracker zooKeeperStateTracker;
    private ZkWriter zkWriter;
    private ZkWatchDog watchDog;
    @Getter
    private String region;

    private LifecycleEvent event;

    private long messageId = 0;

    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        KafkaChannel kafkaChannel = moduleContext.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        region = kafkaChannel.getRegion();
        String connectionString = kafkaChannel.getConfig().getZooKeeperConnectString();
        zkWriter = ZkWriter.builder().id(region).serviceName(ZK_COMPONENT_NAME)
                .connectionString(connectionString).build();
        zooKeeperStateTracker = new ZkStateTracker(zkWriter);

        watchDog = ZkWatchDog.builder().id(region).serviceName(ZK_COMPONENT_NAME)
                .connectionString(connectionString).build();
        watchDog.subscribe(this);
        initZookeeper();
    }

    /**
     * Connects to zookeeper.
     */
    private void initZookeeper() {
        zkWriter.initAndWaitConnection();
        watchDog.initAndWaitConnection();
        forceReadSignal();
    }

    @Override
    public synchronized void handle(Signal signal) {
        log.info("Component {} with id {} received signal {}", ZK_COMPONENT_NAME, region, signal);
        this.event = LifecycleEvent.builder()
                .signal(signal)
                .uuid(UUID.randomUUID())
                .messageId(messageId++).build();
        for (ZooKeeperEventObserver observer : new HashSet<>(observers)) {
            observer.handleLifecycleEvent(event);
        }
    }

    /**
     * Adds observer and notify it about previous event.
     */
    public synchronized void subscribe(ZooKeeperEventObserver observer) {
        if (event != null) {
            observer.handleLifecycleEvent(event);
        }
        observers.add(observer);
    }

    public synchronized void unsubscribe(ZooKeeperEventObserver observer) {
        observers.remove(observer);
    }

    public synchronized void processLifecycleEvent(LifecycleEvent event) {
        zooKeeperStateTracker.processLifecycleEvent(event);
    }

    private void forceReadSignal() {
        Signal signal = null;
        try {
            signal = watchDog.getSignalSync();
        } catch (KeeperException | InterruptedException e) {
            log.error(String.format("Couldn't get signal for component %s and id %s. Error: %s",
                    ZK_COMPONENT_NAME, region, e.getMessage()), e);
        }

        if (signal == null) {
            log.error("Couldn't get signal for component {} and id {}. Signal is null.", ZK_COMPONENT_NAME, region);
        } else {
            handle(signal);
        }
    }
}
