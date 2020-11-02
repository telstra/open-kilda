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

package org.openkilda.wfm.topology.network.controller.bfd;

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.model.BfdSessionData;
import org.openkilda.wfm.topology.network.utils.SwitchOnlineStatusListener;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BfdSessionController implements SwitchOnlineStatusListener {
    private final BfdSessionFsm.BfdSessionFsmFactory fsmFactory;

    @Getter
    private final Endpoint logical;
    private final int physicalPortNumber;

    private BfdSessionManager manager;
    private BfdSessionData sessionData;

    public BfdSessionController(
            BfdSessionFsm.BfdSessionFsmFactory fsmFactory, Endpoint logical, int physicalPortNumber) {
        this.fsmFactory = fsmFactory;

        this.logical = logical;
        this.physicalPortNumber = physicalPortNumber;

        manager = new BfdSessionDummy(fsmFactory.getCarrier(), logical, physicalPortNumber);

        fsmFactory.getSwitchOnlineStatusMonitor().subscribe(logical.getDatapath(), this);
    }

    public void enableUpdate(BfdSessionData sessionData) {
        this.sessionData = sessionData;
        rotate();
    }

    /**
     * Handle BFD session disable/remove request.
     */
    public void disable() {
        sessionData = null;
        if (manager.disable()) {
            rotate();
        }
    }

    public void speakerResponse(String key) {
        manager.speakerResponse(key);
    }

    public void speakerResponse(String key, BfdSessionResponse response) {
        manager.speakerResponse(key, response);
    }

    /**
     * Handle manager's complete notification - cleanup resource, do mangers rotation.
     */
    public void handleCompleteNotification(boolean error) {
        manager = new BfdSessionDummy(fsmFactory.getCarrier(), logical, physicalPortNumber);
        if (! error) {
            rotate();
        } // in case of error new attempt will be done on explicit enable request or on switch offline-to-online event
    }

    @Override
    public void switchOnlineStatusUpdate(boolean isOnline) {
        if (isOnline && manager.isDummy()) {
            rotate();
        }
    }

    private void rotate() {
        if (sessionData == null) {
            emitCompleteNotification();
            return;
        }

        if (! manager.isOperationalAndEqualTo(sessionData)) {
            if (manager.disable()) {
                manager = fsmFactory.produce(logical, physicalPortNumber, sessionData);
                log.info("Rotate existing BFD session on {}, new configuration: {}", logical, sessionData);
            } else {
                log.info("Initiate BFD session termination on {}", logical);
            }
        } else {
            log.info("The existing BFD session on {} is operational and uses the same configuration as requested, no "
                    + "need to rotate. (configuration: {})", logical, sessionData);
        }
    }

    private void emitCompleteNotification() {
        fsmFactory.getCarrier().sessionCompleteNotification(Endpoint.of(logical.getDatapath(), physicalPortNumber));
    }
}
