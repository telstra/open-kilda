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

package org.openkilda.bluegreen.kafka.interceptors;

import org.openkilda.bluegreen.BuildVersionObserver;
import org.openkilda.bluegreen.ZkClient;
import org.openkilda.bluegreen.ZkWatchDog;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
public abstract class VersioningInterceptorBase implements BuildVersionObserver {
    public static final int VERSION_IS_NOT_SET_LOG_TIMEOUT = 60;
    public static final int CANT_CONNECT_TO_ZOOKEEPER_LOG_TIMEOUT = 60;
    public static final int ZOOKEEPER_RECONNECTION_ATTEMPTS = 10;

    protected String connectionString;
    protected ZkWatchDog watchDog;
    protected String componentName;
    protected String runId;
    protected Instant versionIsNotSetTimestamp = Instant.MIN;
    protected Instant cantConnectToZooKeeperTimestamp = Instant.MIN;
    protected volatile byte[] version;

    protected boolean isVersionTimeoutPassed() {
        return versionIsNotSetTimestamp.plus(VERSION_IS_NOT_SET_LOG_TIMEOUT, ChronoUnit.SECONDS)
                .isBefore(Instant.now());
    }

    protected boolean isZooKeeperConnectTimeoutPassed() {
        return cantConnectToZooKeeperTimestamp.plus(CANT_CONNECT_TO_ZOOKEEPER_LOG_TIMEOUT, ChronoUnit.SECONDS)
                .isBefore(Instant.now());
    }

    protected String getVersionAsString() {
        if (version == null) {
            return "null";
        }
        return new String(version);
    }

    protected void initWatchDog() {
        watchDog = ZkWatchDog.builder()
                .id(runId)
                .serviceName(componentName)
                .connectionString(connectionString)
                .connectionRefreshInterval(ZkClient.DEFAULT_CONNECTION_REFRESH_INTERVAL)
                .build();
        watchDog.subscribe(this);

        for (int i = 1; i <= ZOOKEEPER_RECONNECTION_ATTEMPTS && !watchDog.isConnectedAndValidated(); i++) {
            log.info("Component {} with id {} string to reconnect to ZooKeeper {} Attempt: {}",
                    componentName, runId, connectionString, i);
            watchDog.init();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.debug(String.format("Component %s with id %s and connection string %s caught exception during "
                                + "waiting for zookeeper watchdog initialized",
                        componentName, runId, connectionString), e);
            }
        }
    }
}
