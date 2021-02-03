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
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ZooKeeperService implements IService, LifeCycleObserver {

    public static final String ZK_COMPONENT_NAME = "floodlight";

    private final Set<ZooKeeperEventObserver> observers = new HashSet<>();

    @Getter
    private ZkStateTracker zooKeeperStateTracker;
    private ZkWriter zkWriter;
    private ZkWatchDog watchDog;

    private LifecycleEvent event;

    private long messageId = 0;

    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        KafkaChannel kafkaChannel = moduleContext.getServiceImpl(KafkaUtilityService.class).getKafkaChannel();
        String region = kafkaChannel.getRegion();
        String connectionString = kafkaChannel.getConfig().getZooKeeperConnectString();
        zkWriter = ZkWriter.builder().id(region).serviceName(ZK_COMPONENT_NAME)
                .connectionString(connectionString).build();
        zooKeeperStateTracker = new ZkStateTracker(zkWriter);

        watchDog = ZkWatchDog.builder().id(region).serviceName(ZK_COMPONENT_NAME)
                .connectionString(connectionString).build();
        watchDog.subscribe(this);
    }

    /**
     * Connects to zookeeper.
     */
    public void initZookeeper() {
        zkWriter.init();
        watchDog.init();

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(IllegalStateException.class)
                .withDelay(100, TimeUnit.MILLISECONDS);

        SyncFailsafe failsafe = Failsafe.with(retryPolicy)
                .onRetry(e -> log.error("Failed to init zk clients, retrying...", e))
                .onRetriesExceeded(e -> log.error("Failure in zookeeper initialization. No more retries", e));
        failsafe.run(() -> verifyAndRefreshZk());
    }

    private void verifyAndRefreshZk() {
        boolean retry = false;
        if (!zkWriter.isConnectedAndValidated()) {
            retry = true;
            zkWriter.safeRefreshConnection();
        }

        if (!watchDog.isConnectedAndValidated()) {
            retry = true;
            watchDog.safeRefreshConnection();
        }

        if (retry) {
            throw new IllegalStateException("Failed to validate zookeeper connection/state");
        }
    }

    @Override
    public synchronized void handle(Signal signal) {
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
}
