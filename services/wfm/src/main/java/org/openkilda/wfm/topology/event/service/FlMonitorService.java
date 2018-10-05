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

package org.openkilda.wfm.topology.event.service;

import org.openkilda.messaging.HeartBeat;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.share.utils.WatchDog;
import org.openkilda.wfm.topology.event.bolt.FlMonitor.OutputAdapter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlMonitorService {
    private final WatchDog connectWatchDog;
    private final long dumpRequestTimeout;
    private SyncProcess syncProcess;

    @VisibleForTesting
    State state = State.NEED_SYNC;

    public FlMonitorService(long speakerOutagePeriod, long dumpRequestTimeout, long timestamp) {
        this.connectWatchDog = new WatchDog("speaker", speakerOutagePeriod, timestamp);
        this.dumpRequestTimeout = dumpRequestTimeout;
    }

    /**
     * Handle/proxy speaker message.
     */
    public void speakerMessage(OutputAdapter outputAdapter, Message message) {
        connectWatchDog.reset(outputAdapter.getContext().getCreateTime());

        boolean isHeartBeat = message instanceof HeartBeat;
        if (isHeartBeat) {
            log.debug("Got speaker's heart beat");
        }

        switch (state) {
            case MAIN:
                if (!isHeartBeat) {
                    outputAdapter.proxyCurrentTuple();
                }
                break;

            case WAIT_SYNC:
                if (!isHeartBeat) {
                    feedSync(outputAdapter, message);
                }
                break;

            case OFFLINE:
                log.warn("Got input while in offline mode, it indicate recovery of kafka channel");
                stateTransition(State.NEED_SYNC);
                break;

            default:
                reportUnhandledEvent(message.getClass().getCanonicalName());
        }
    }

    /**
     * Handle timer event.
     */
    public void timerTick(OutputAdapter outputAdapter, long timeMillis) {
        switch (state) {
            case NEED_SYNC:
                initSync(outputAdapter, timeMillis);
                break;

            case WAIT_SYNC:
                if (syncProcess.isStale(timeMillis)) {
                    log.error("Did not get network dump, send one more dump request");
                    initSync(outputAdapter, timeMillis);
                }
                break;

            case MAIN:
                if (connectWatchDog.detectFailure(timeMillis)) {
                    stateTransition(State.OFFLINE);
                }
                break;

            case OFFLINE:
                break;

            default:
                reportUnhandledEvent("timerTick");
        }
    }

    private void initSync(OutputAdapter outputAdapter, long timestamp) {
        syncProcess = new SyncProcess(outputAdapter, timestamp, dumpRequestTimeout);
        stateTransition(State.WAIT_SYNC);
    }

    private void feedSync(OutputAdapter outputAdapter, Message message) {
        if (message instanceof InfoMessage) {
            syncProcess.input((InfoMessage) message);
            if (syncProcess.isComplete()) {
                completeSync(outputAdapter);
            }
        } else {
            reportUnhandledEvent(message.getClass().getName());
        }
    }

    private void completeSync(OutputAdapter outputAdapter) {
        outputAdapter.shareSync(syncProcess.getPayload());
        syncProcess = null;
        stateTransition(State.MAIN);
    }

    private void stateTransition(State switchTo) {
        log.info("State transition to {} (current {})", switchTo, state);
        state = switchTo;
    }

    private void reportUnhandledEvent(String eventDetails) {
        log.error("State {}: can\'t handle {} event", state, eventDetails);
    }

    @VisibleForTesting
    enum State {
        NEED_SYNC,
        WAIT_SYNC,
        OFFLINE,
        MAIN
    }
}
