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

package org.openkilda.bluegreen;

import static java.lang.String.format;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@SuperBuilder
public abstract class ZkClient implements Watcher {
    private static final String ROOT = "/";
    private static final int DEFAULT_SESSION_TIMEOUT = 30000;
    public static final long DEFAULT_CONNECTION_REFRESH_INTERVAL = 10;
    public static final int RECONNECT_DELAY_MS = 100;

    @Getter
    protected String id;
    @Getter
    protected String serviceName;
    protected volatile ZooKeeper zookeeper;
    protected final String connectionString;
    protected boolean nodesValidated;
    private final int sessionTimeout;
    protected Instant lastRefreshAttempt;
    protected long connectionRefreshInterval;
    protected long reconnectDelayMs;

    public ZkClient(String id, String serviceName, String connectionString, int sessionTimeout,
                    long connectionRefreshInterval, long reconnectDelayMs) {
        if (sessionTimeout == 0) {
            sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        }
        this.nodesValidated = false;
        this.serviceName = serviceName;
        this.id = id;
        this.connectionString = connectionString;
        this.sessionTimeout = sessionTimeout;
        this.connectionRefreshInterval = connectionRefreshInterval;
        this.lastRefreshAttempt = Instant.MIN;
        if (reconnectDelayMs <= 0) {
            reconnectDelayMs = RECONNECT_DELAY_MS;
        }
        this.reconnectDelayMs = reconnectDelayMs;
    }

    /**
     * Init client and try to reconnect if needed.
     */
    public synchronized void initAndWaitConnection() {
        init();

        RetryPolicy<Void> retryPolicy = new RetryPolicy<Void>()
                .handle(IllegalStateException.class)
                .withMaxRetries(-1)
                .withDelay(Duration.ofMillis(reconnectDelayMs))
                .onRetry(e -> {
                    String message = format("Failed to init zk client, retrying... Attempt: %d", e.getAttemptCount());
                    if (e.getAttemptCount() <= 10) {
                        log.info(message, e.getLastFailure());
                    } else if (e.getAttemptCount() <= 20) {
                        log.warn(message, e.getLastFailure());
                    } else {
                        log.error(message, e.getLastFailure());
                    }
                });

        Failsafe.with(retryPolicy)
                .run(this::reconnect);
    }

    private void reconnect() {
        if (isConnectedAndValidated()) {
            return;
        }
        refreshConnection();

        if (!isConnectedAndValidated()) {
            throw new IllegalStateException(format(
                    "Failed to validate zookeeper connection/state for component %s with id %s", serviceName, id));
        }
    }

    /**
     * Init client.
     */
    @VisibleForTesting
    void init() {
        try {
            if (!isAlive()) {
                initZk();
            }
        } catch (IOException e) {
            log.error(format("Couldn't init ZooKeeper for component %s with run id %s and "
                    + "connection string %s. Error: %s", serviceName, id, connectionString, e.getMessage()), e);
            closeZk();
        }
        if (isConnected()) {
            try {
                validateZNodes();
            } catch (KeeperException | InterruptedException | IllegalStateException e) {
                log.error(format("Couldn't validate nodes for component %s with run id %s and "
                        + "connection string %s. Error: %s", serviceName, id, connectionString, e.getMessage()), e);

            }
            subscribeNodes();
        } else {
            log.info("Skip zk nodes validation for component {} with run id {}", serviceName, id);
        }
    }

    String getPaths(String... paths) {
        return ROOT + String.join("/", paths);
    }

    /**
     * Refreshes connection if state shows that connection is broken and refresh interval passed.
     */
    boolean refreshConnectionIfNeeded(KeeperState state) {
        if ((state == KeeperState.Disconnected || state == KeeperState.Expired) && isRefreshIntervalPassed()) {
            safeRefreshConnection();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    boolean isRefreshIntervalPassed() {
        return Instant.now().isAfter(lastRefreshAttempt.plus(connectionRefreshInterval, ChronoUnit.SECONDS));
    }

    void closeZk() {
        if (zookeeper != null) {
            try {
                log.info("Going to close zookeeper connection 0x{}", Long.toHexString(zookeeper.getSessionId()));
                zookeeper.close();
            } catch (InterruptedException e) {
                log.warn(format("Failed to close connection to zk %s", connectionString), e);
            }
        }
    }

    void initZk() throws IOException {
        nodesValidated = false;
        zookeeper = getZk();
    }

    @VisibleForTesting
    ZooKeeper getZk() throws IOException {
        return new ZooKeeper(connectionString, sessionTimeout, this);
    }

    /**
     * Attempt to refresh connection.
     */
    @VisibleForTesting
    synchronized void refreshConnection() {
        if (!isConnected()) {
            log.info("Closing connection for session 0x{} due to state active={}",
                    zookeeper != null ? Long.toHexString(zookeeper.getSessionId()) : 0L, isConnected());
            closeZk();
        }
        init();
    }

    /**
     * Attempt to refresh connection after specified interval of time.
     */
    public synchronized void safeRefreshConnection() {
        if (!isConnectedAndValidated() && isRefreshIntervalPassed()) {
            refreshConnection();
            lastRefreshAttempt = Instant.now();
        } else {
            if (log.isTraceEnabled()) {
                log.trace("Skipping connection {} safe refresh for zookeeper client {}/{}", connectionString,
                        serviceName, id);
            }
        }

    }

    protected void ensureZNode(String... path) throws KeeperException, InterruptedException {
        ensureZNode(new byte[0], path); // by default we set empty value for zookeeper node
    }

    protected void ensureZNode(byte[] value, String... path) throws KeeperException, InterruptedException {
        String nodePath = getPaths(path);
        if (zookeeper.exists(nodePath, false) == null) {
            try {
                zookeeper.create(nodePath, value,
                        Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (Exception e) {
                log.error(format("Failed to ensure node: %s. Error: %s", nodePath, e.getMessage()), e);
            }
        }
        if (zookeeper.exists(nodePath, false) == null) {
            String message = format("Zk node %s still does not exists", nodePath);
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    @VisibleForTesting
    void validateZNodes() throws KeeperException, InterruptedException {
        ensureZNode(serviceName);
        ensureZNode(serviceName, id);
        validateNodes();
        nodesValidated = true;

    }

    abstract void subscribeNodes();

    abstract void validateNodes() throws KeeperException, InterruptedException;

    /**
     * Checks that connection to zookeeper is alive.
     */
    boolean isAlive() {
        return zookeeper != null && zookeeper.getState().isAlive();
    }

    /**
     * Checks that connection to zookeeper is connected.
     */
    boolean isConnected() {
        // there is a state CONNECTING, that doesn't allow to use getState().isAlive()
        return zookeeper != null && zookeeper.getState().isConnected();
    }

    /**
     * Checks whether client is in operational state.
     */
    public boolean isConnectedAndValidated() {
        return isConnected() && nodesValidated;
    }
}
