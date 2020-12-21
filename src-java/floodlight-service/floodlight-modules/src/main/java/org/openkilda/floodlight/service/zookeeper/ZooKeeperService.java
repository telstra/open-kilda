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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class ZooKeeperService implements IService, LifeCycleObserver {

    public static final String ZK_COMPONENT_NAME = "floodlight";
    public static final int ZK_CONNECTION_ATTEMPTS = 10;

    private final Set<ZooKeeperEventObserver> observers = new HashSet<>();

    @Getter
    private ZkStateTracker zooKeeperStateTracker;
    private ZkWriter zkWriter;
    private ZkWatchDog watchDog;

    private LifecycleEvent event;

    private long messageId = 0;

    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        initZookeeper(moduleContext);
        for (int i = 1; i <= ZK_CONNECTION_ATTEMPTS
                && (!zkWriter.isConnectedAndValidated() || !watchDog.isConnectedAndValidated()); i++) {
            log.error("Failed to init zk clients, retrying {} of {}", i, ZK_CONNECTION_ATTEMPTS);
            if (!zkWriter.isConnectedAndValidated()) {
                zkWriter.safeRefreshConnection();
            }
            if (!watchDog.isConnectedAndValidated()) {
                watchDog.safeRefreshConnection();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("Caught exception during waiting for zookeeper service initialization", e);
            }
        }
    }

    private void initZookeeper(FloodlightModuleContext moduleContext) {
        KafkaChannel kafkaChannel = moduleContext.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        String region = kafkaChannel.getRegion();
        String connectionString = kafkaChannel.getConfig().getZooKeeperConnectString();
        zkWriter = ZkWriter.builder().id(region).serviceName(ZK_COMPONENT_NAME)
                .connectionString(connectionString).build();
        zkWriter.init();
        zooKeeperStateTracker = new ZkStateTracker(zkWriter);

        watchDog = ZkWatchDog.builder().id(region).serviceName(ZK_COMPONENT_NAME)
                .connectionString(connectionString).build();
        watchDog.init();
        watchDog.subscribe(this);
    }

    @Override
    public void handle(Signal signal) {
        this.event = LifecycleEvent.builder()
                .signal(signal)
                .uuid(UUID.randomUUID())
                .messageId(messageId++).build();
        for (ZooKeeperEventObserver observer : observers) {
            observer.handleLifecycleEvent(event);
        }
    }

    public void subscribe(ZooKeeperEventObserver observer) {
        observers.add(observer);
    }

    public void unsubscribe(ZooKeeperEventObserver observer) {
        observers.remove(observer);
    }
}
