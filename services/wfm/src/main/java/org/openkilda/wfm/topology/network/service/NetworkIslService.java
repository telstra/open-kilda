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

package org.openkilda.wfm.topology.network.service;

import org.openkilda.messaging.info.event.IslBfdFlagUpdated;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.IslFsm;
import org.openkilda.wfm.topology.network.controller.IslFsm.IslFsmContext;
import org.openkilda.wfm.topology.network.controller.IslFsm.IslFsmEvent;
import org.openkilda.wfm.topology.network.controller.IslFsm.IslFsmState;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.storm.bolt.isl.BfdManager;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NetworkIslService {
    private final IslFsm.IslFsmFactory controllerFactory;
    private final Map<IslReference, IslController> controller = new HashMap<>();
    private final FsmExecutor<IslFsm, IslFsmState, IslFsmEvent, IslFsmContext> controllerExecutor;

    private final IIslCarrier carrier;
    private final NetworkOptions options;

    public NetworkIslService(IIslCarrier carrier, PersistenceManager persistenceManager, NetworkOptions options) {
        this.carrier = carrier;
        this.options = options;

        controllerFactory = IslFsm.factory(persistenceManager);
        controllerExecutor = controllerFactory.produceExecutor();
    }

    /**
     * Create ISL handler and use "history" data to initialize it's state.
     */
    public void islSetupFromHistory(Endpoint endpoint, IslReference reference, Isl history) {
        log.info("ISL service receive SETUP request from history data for {} (on {})", reference, endpoint);
        if (!controller.containsKey(reference)) {
            ensureControllerIsMissing(reference);

            IslController islController = new IslController(controllerFactory, options, reference);
            controller.put(reference, islController);
            IslFsmContext context = IslFsmContext.builder(carrier, endpoint)
                    .history(history)
                    .build();
            controllerExecutor.fire(islController.fsm, IslFsmEvent.HISTORY, context);
        } else {
            log.error("Receive HISTORY data for already created ISL - ignore history "
                              + "(possible start-up race condition)");
        }
    }

    /**
     * .
     */
    public void islUp(Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        log.debug("ISL service receive DISCOVERY notification for {} (on {})", reference, endpoint);
        IslFsm islFsm = locateControllerCreateIfAbsent(reference).fsm;
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint)
                .islData(islData)
                .build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_UP, context);
    }

    /**
     * .
     */
    public void islDown(Endpoint endpoint, IslReference reference, IslDownReason reason) {
        log.debug("ISL service receive FAIL notification for {} (on {})", reference, endpoint);
        IslFsm islFsm = locateController(reference).fsm;
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint)
                .downReason(reason)
                .build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_DOWN, context);
    }

    /**
     * .
     */
    public void islMove(Endpoint endpoint, IslReference reference) {
        log.debug("ISL service receive MOVED(FAIL) notification for {} (on {})", reference, endpoint);
        IslFsm islFsm = locateController(reference).fsm;
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint).build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_MOVE, context);
    }

    /**
     * Process enable/disable BFD requests.
     */
    public void bfdEnableDisable(IslReference reference, IslBfdFlagUpdated payload) {
        log.debug("ISL service receive allow-BFD switch update notification for {} new-status:{}",
                  reference, payload.isEnableBfd());
        BfdManager bfdManager = locateController(reference).bfdManager;
        if (payload.isEnableBfd()) {
            bfdManager.enable(carrier);
        } else {
            bfdManager.disable(carrier);
        }
    }

    /**
     * Remove isl by request.
     */
    public void remove(IslReference reference) {
        log.debug("ISL service receive remove for {}", reference);

        IslController islController = controller.get(reference);
        if (islController == null) {
            log.info("Got DELETE request for not existing ISL {}", reference);
            return;
        }

        IslFsm fsm = islController.fsm;
        IslFsmContext context = IslFsmContext.builder(carrier, reference.getSource())
                .build();
        controllerExecutor.fire(fsm, IslFsmEvent.ISL_REMOVE, context);
        if (fsm.isTerminated()) {
            controller.remove(reference);
            islController.bfdManager.disable(carrier);
            log.info("ISL {} have been removed", reference);
        } else {
            log.error("ISL service remove failed for FSM {}, state: {}", reference, fsm.getCurrentState());
        }
    }

    // -- private --

    private void ensureControllerIsMissing(IslReference reference) {
        IslController islController = controller.get(reference);
        if (islController != null) {
            throw new IllegalStateException(String.format("ISL FSM for %s already exist (it's state is %s)",
                                                          reference, islController.fsm.getCurrentState()));
        }
    }

    private IslController locateController(IslReference reference) {
        IslController islController = controller.get(reference);
        if (islController == null) {
            throw new IllegalStateException(String.format("There is no ISL FSM for %s", reference));
        }
        return islController;
    }

    private IslController locateControllerCreateIfAbsent(IslReference reference) {
        return controller.computeIfAbsent(reference, key -> new IslController(controllerFactory, options, reference));
    }

    private static final class IslController {
        private final IslFsm fsm;
        private final BfdManager bfdManager;

        private IslController(IslFsm.IslFsmFactory controllerFactory, NetworkOptions options, IslReference reference) {
            bfdManager = new BfdManager(reference);
            fsm = controllerFactory.produce(bfdManager, options, reference);
        }
    }
}
