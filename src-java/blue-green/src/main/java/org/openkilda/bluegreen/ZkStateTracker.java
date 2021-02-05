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

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class ZkStateTracker {
    private final ZkWriter zooKeeperWriter;
    UUID shutdownUuid;
    UUID startUuid;
    int active;

    public ZkStateTracker(ZkWriter zooKeeperWriter) {
        this.zooKeeperWriter = zooKeeperWriter;
    }

    /**
     * Process new lifecycle event.
     */
    public void processLifecycleEvent(LifecycleEvent event) {
        int oldActive = active;
        if (Signal.START == event.getSignal()) {
            shutdownUuid = null;
            handleStart(event);
        } else if (Signal.SHUTDOWN == event.getSignal()) {
            handleShutdown(event);
            startUuid = null;
        }
        zooKeeperWriter.setState(active);
        log.info("State of {} with id {} was changed from {} to {}",
                zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), oldActive, active);
    }

    private void handleStart(LifecycleEvent event) {
        if (startUuid != null) {
            if (startUuid.equals(event.getUuid())) {
                active++;
                log.info("Component {} with id {} got start event with known UUID {}. Active is {} now",
                        zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), startUuid, active);
            } else {
                log.info("Component {} with id {} got start event with different UUID. Old start UUID: {}, "
                                + "new UUID {}. Resetting active from {} to 1.",
                        zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), startUuid, event.getUuid(), active);
                startUuid = event.getUuid();
                active = 1;
            }
        } else {
            startUuid = event.getUuid();
            active++;
            log.info("Component {} with id {} got new start event. Set start UUID to {}. Active is {} now",
                    zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), startUuid, active);
        }
    }

    private void handleShutdown(LifecycleEvent event) {
        if (shutdownUuid != null) {
            if (shutdownUuid.equals(event.getUuid())) {
                decrementActive();
                log.info("Component {} with id {} got shutdown event with known UUID {}. Active is {} now",
                        zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), shutdownUuid, active);
            } else {
                log.error("Unknown lifecycle shutdown event received uuid: {}, expected: {}. Active is {}",
                        event.getUuid(), shutdownUuid, active);
            }
        } else {
            shutdownUuid = event.getUuid();
            decrementActive();
            log.info("Component {} with id {} got new shutdown event. Set shutdown UUID to {}. Active is {} now",
                    zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), shutdownUuid, active);
        }
    }

    private void decrementActive() {
        if (active > 0) {
            active--;
        } else {
            log.warn("Component {} with id {} couldn't decrement active {} because it will be negative. "
                    + "Set active to 0.", zooKeeperWriter.getServiceName(), zooKeeperWriter.getId(), active);
            active = 0;
        }
    }

    private void resetActive() {
        active = 0;
        startUuid = null;
    }

}
