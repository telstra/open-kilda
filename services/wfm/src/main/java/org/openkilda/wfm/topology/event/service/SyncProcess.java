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

import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkDumpBeginMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpEndMarker;
import org.openkilda.messaging.info.discovery.NetworkDumpPortData;
import org.openkilda.messaging.info.discovery.NetworkDumpSwitchData;
import org.openkilda.wfm.topology.event.bolt.FlMonitor.OutputAdapter;
import org.openkilda.wfm.topology.event.model.Sync;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncProcess {
    private final String correlationId;
    private final long timeEnd;
    @Getter
    private boolean complete = false;

    @Getter
    private final Sync payload = new Sync();

    public SyncProcess(OutputAdapter outputAdapter, long timestamp, long timeout) {
        correlationId = outputAdapter.getContext().getCorrelationId();
        timeEnd = timestamp + timeout;

        sendRequest(outputAdapter);
    }

    /**
     * Handle incoming message.
     *
     * <p>Filter sync events and store meaningful data into payload.
     */
    public void input(InfoMessage message) {
        boolean handled = false;
        if (correlationId.equals(message.getCorrelationId())) {
            handled = dispatch(message.getData());
        }
        if (!handled) {
            ignoreInput(message);
        }
    }

    public boolean isStale(long timestamp) {
        return !complete && timeEnd < timestamp;
    }

    private void sendRequest(OutputAdapter outputAdapter) {
        log.info("Send network dump request (correlation-id: {})", correlationId);
        outputAdapter.speakerCommand(new NetworkCommandData());
    }

    private boolean dispatch(InfoData payload) {
        if (payload instanceof NetworkDumpBeginMarker) {
            handleBegin();
        } else if (payload instanceof NetworkDumpEndMarker) {
            handleEnd();
        } else if (payload instanceof NetworkDumpSwitchData) {
            handleSwitchDump((NetworkDumpSwitchData) payload);
        } else if (payload instanceof NetworkDumpPortData) {
            handlePortDump((NetworkDumpPortData) payload);
        } else {
            return false;
        }

        return true;
    }

    private void handleBegin() {
        log.info("Got FL sync begin marker");
    }

    private void handleEnd() {
        log.info("Got FL sync end marker");
        complete = true;
    }

    private void handleSwitchDump(NetworkDumpSwitchData switchDump) {
        log.info("Got FL sync switch data: {}", switchDump.getSwitchId());
        payload.addActiveSwitch(switchDump.getSwitchId());
    }

    private void handlePortDump(NetworkDumpPortData portDump) {
        log.info("Got FL sync port data: {} port{}", portDump.getSwitchId(), portDump.getPortNo());
        payload.addActivePort(portDump.getSwitchId(), portDump.getPortNo());
    }

    private void ignoreInput(InfoMessage message) {
        log.warn("Drop incoming message {} because it is not a part of FL sync process", message.getClass().getName());
    }
}
