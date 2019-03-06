/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.discovery.service;

import org.openkilda.messaging.info.event.IslBfdFlagUpdated;
import org.openkilda.model.Isl;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.IslFsm;
import org.openkilda.wfm.topology.discovery.controller.IslFsm.IslFsmContext;
import org.openkilda.wfm.topology.discovery.controller.IslFsm.IslFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.IslFsm.IslFsmState;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslDataHolder;
import org.openkilda.wfm.topology.discovery.model.IslReference;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryIslService {
    private final Map<IslReference, IslFsm> controller = new HashMap<>();

    private final FsmExecutor<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> controllerExecutor
            = IslFsm.makeExecutor();

    private final PersistenceManager persistenceManager;
    private final DiscoveryOptions options;

    public DiscoveryIslService(PersistenceManager persistenceManager, DiscoveryOptions options) {
        this.persistenceManager = persistenceManager;
        this.options = options;
    }

    /**
     * Create ISL handler and use "history" data to initialize it's state.
     */
    public void islSetupFromHistory(Endpoint endpoint, IslReference reference, Isl history) {
        log.debug("ISL service receive SETUP request from history data for {} (on {})", reference, endpoint);
        if (!controller.containsKey(reference)) {
            ensureControllerIsMissing(reference);
            controller.put(reference, IslFsm.createFromHistory(persistenceManager, options, reference, history));
        } else {
            log.error("Receive HISTORY data for already created ISL - ignore history "
                              + "(possible start-up race condition)");
        }
    }

    /**
     * .
     */
    public void islUp(IIslCarrier carrier, Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        log.debug("ISL service receive DISCOVERY notification for {} (on {})", reference, endpoint);
        IslFsm islFsm = locateControllerCreateIfAbsent(reference);
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint)
                .islData(islData)
                .build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_UP, context);
    }

    /**
     * .
     */
    public void islDown(IIslCarrier carrier, Endpoint endpoint, IslReference reference, boolean isPhysicalDown) {
        log.debug("ISL service receive FAIL notification for {} (on {})", reference, endpoint);
        IslFsm islFsm = locateController(reference);
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint)
                .physicalLinkDown(isPhysicalDown)
                .build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_DOWN, context);
    }

    /**
     * .
     */
    public void islMove(IIslCarrier carrier, Endpoint endpoint, IslReference reference) {
        log.debug("ISL service receive MOVED(FAIL) notification for {} (on {})", reference, endpoint);
        IslFsm islFsm = locateController(reference);
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint).build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_MOVE, context);
    }

    /**
     * Process enable/disable BFD requests.
     */
    public void bfdEnableDisable(IIslCarrier carrier, IslReference reference, IslBfdFlagUpdated payload) {
        log.debug("ISL service receive allow-BFD switch update notification for {} new-status:{}",
                  reference, payload.isEnableBfd());
        IslFsm islFsm = locateController(reference);
        IslFsmContext context = IslFsmContext.builder(carrier, reference.getSource())
                .bfdEnable(payload.isEnableBfd())
                .build();
        controllerExecutor.fire(islFsm, IslFsmEvent.BFD_UPDATE, context);
    }

    /**
     * Remove isl by request.
     */
    public void remove(IIslCarrier carrier, IslReference reference) {
        IslFsm fsm = controller.get(reference);
        if (fsm != null) {
            IslFsmContext context = IslFsmContext.builder(carrier, reference.getSource())
                    .build();
            controllerExecutor.fire(fsm, IslFsmEvent.ISL_REMOVE, context);
            if (fsm.getCurrentState() == IslFsmState.DELETED) {
                controller.remove(reference);
                log.debug("ISL service removed FSM {}", reference);
            } else {
                log.error("ISL service remove failed for FSM {}, state: {}", reference, fsm.getCurrentState());
            }
        }
    }

    // -- private --

    private void ensureControllerIsMissing(IslReference reference) {
        IslFsm fsm = controller.get(reference);
        if (fsm != null) {
            throw new IllegalStateException(String.format("ISL FSM for %s already exist (it's state is %s)",
                                                          reference, fsm.getCurrentState()));
        }
    }

    private IslFsm locateController(IslReference reference) {
        IslFsm fsm = controller.get(reference);
        if (fsm == null) {
            throw new IllegalStateException(String.format("There is not ISL FSM for %s", reference));
        }
        return fsm;
    }

    private IslFsm locateControllerCreateIfAbsent(IslReference reference) {
        return controller.computeIfAbsent(reference, key -> IslFsm.create(persistenceManager, options, reference));
    }
}
