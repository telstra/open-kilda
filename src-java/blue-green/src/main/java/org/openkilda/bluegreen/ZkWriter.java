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

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;

@Slf4j
public class ZkWriter extends ZkClient {
    private static final String STATE = "state";
    private static final String EXPECTED_STATE = "expected_state";
    private final String statePath;
    private final String expectedStatePath;
    private final int expectedState;

    @Builder
    public ZkWriter(String id, String serviceName, String connectionString, int sessionTimeout,
                    long connectionRefreshInterval, final int expectedState) {
        super(id, serviceName, connectionString, sessionTimeout, connectionRefreshInterval);
        statePath = getPaths(serviceName, id, STATE);
        expectedStatePath = getPaths(serviceName, id, EXPECTED_STATE);
        this.expectedState = expectedState;
    }

    @Override
    public synchronized void initAndWaitConnection() {
        super.initAndWaitConnection();
        setExpectedState(expectedState);
    }

    /**
     * Sets state for service.
     */
    public void setState(int active) {
        setData(statePath, active);
    }

    /**
     * Sets expected state for service.
     */
    private void setExpectedState(int expectedState) {
        setData(expectedStatePath, expectedState);
    }

    @Override
    void validateNodes() throws KeeperException, InterruptedException {
        ensureZNode(serviceName, id, STATE);
        ensureZNode(serviceName, id, EXPECTED_STATE);
    }

    @Override
    void subscribeNodes() {

    }

    @Override
    public void process(WatchedEvent event) {
        log.info("Received event: {}", event);
        refreshConnectionIfNeeded(event.getState());
    }

    private void setData(String path, int value) {
        try {
            zookeeper.setData(path, Integer.toString(value).getBytes(), -1);
        } catch (KeeperException e) {
            log.error("Zk keeper error: {}", e.getMessage(), e);
            throw new IllegalStateException(String.format("Failed to set data %s to node %s", value, path));
        } catch (InterruptedException e) {
            log.error("Zk event interrupted: {}", e.getMessage(), e);
            throw new IllegalStateException(String.format("Failed to set data %s to node %s", value, path));
        }
    }
}
