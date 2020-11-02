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

package org.openkilda.wfm.topology.network.service;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.model.BfdProperties;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.controller.bfd.BfdLogicalPortFsm;
import org.openkilda.wfm.topology.network.controller.bfd.BfdLogicalPortFsm.BfdLogicalPortFsmContext;
import org.openkilda.wfm.topology.network.controller.bfd.BfdLogicalPortFsm.BfdLogicalPortFsmFactory;
import org.openkilda.wfm.topology.network.controller.bfd.BfdLogicalPortFsm.Event;
import org.openkilda.wfm.topology.network.error.BfdLogicalPortControllerNotFoundException;
import org.openkilda.wfm.topology.network.model.BfdSessionData;
import org.openkilda.wfm.topology.network.utils.SwitchOnlineStatusMonitor;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NetworkBfdLogicalPortService {
    private final BfdLogicalPortFsm.BfdLogicalPortFsmFactory controllerFactory;

    private final SwitchOnlineStatusMonitor switchOnlineStatusMonitor;
    private final int logicalPortNumberOffset;

    private final Map<Endpoint, BfdLogicalPortFsm> controllerByEndpoint = new HashMap<>();

    public NetworkBfdLogicalPortService(
            IBfdLogicalPortCarrier carrier, SwitchOnlineStatusMonitor switchOnlineStatusMonitor,
            int logicalPortNumberOffset) {
        this.switchOnlineStatusMonitor = switchOnlineStatusMonitor;
        this.logicalPortNumberOffset = logicalPortNumberOffset;

        controllerFactory = BfdLogicalPortFsm.factory(carrier);
    }

    /**
     * Handle BFD logical port add notification.
     */
    public void portAdd(Endpoint logical, int physicalPortNumber) {
        logServiceCall("PORT-ADD logical={}, physical-port={}", logical, physicalPortNumber);
        Endpoint physical = Endpoint.of(logical.getDatapath(), physicalPortNumber);
        BfdLogicalPortFsm controller = lookupControllerCreateIfMissing(
                physical, logical.getPortNumber());
        handle(controller, Event.PORT_ADD);
    }

    public void portDel(Endpoint logical) {
        logServiceCall("PORT-DEL logical={}", logical);
        handle(lookupController(logical), Event.PORT_DEL);
    }

    public void sessionCompleteNotification(Endpoint physical) {
        logServiceCall("SESSION-COMPLETED notification for physical={}", physical);
        handle(physical, Event.SESSION_COMPLETED);
    }

    /**
     * Handle BFD enable/update/disable requests.
     */
    public void apply(Endpoint physical, IslReference reference, BfdProperties properties) {
        if (properties.isEnabled()) {
            enableUpdate(physical, reference, properties);
        } else {
            disable(physical);
        }
    }

    private void enableUpdate(Endpoint physical, IslReference reference, BfdProperties properties) {
        logServiceCall("ENABLE/UPDATE physical={}, reference={}, properties={}", physical, reference, properties);
        BfdLogicalPortFsm controller = lookupControllerCreateIfMissing(physical);
        BfdLogicalPortFsmContext context = BfdLogicalPortFsmContext.builder()
                .sessionData(new BfdSessionData(reference, properties))
                .build();
        handle(controller, Event.ENABLE_UPDATE, context);
    }

    public void disable(Endpoint physical) {
        logServiceCall("DISABLE physical={}", physical);
        handle(lookupController(physical), Event.DISABLE);
    }

    /**
     * Handle BFD optional disable request (reaction on ISL remove notification).
     */
    public void disableIfExists(Endpoint physical) {
        logServiceCall("DISABLE(if exists) physical={}", physical);
        handle(physical, Event.DISABLE);
    }

    /**
     * Handle worker's success response.
     */
    public void workerSuccess(String requestId, Endpoint logical, InfoData response) {
        logServiceCall("WORKER-SUCCESS requestId={}, logical={}, response={}", requestId, logical, response);
        lookupController(logical)
                .processWorkerSuccess(requestId, response);
    }

    /**
     * Handle worker's error response.
     */
    public void workerError(String requestId, Endpoint logical, ErrorData response) {
        logServiceCall("WORKER-ERROR requestId={}, logical={}, response={}", requestId, logical, response);
        lookupController(logical)
                .processWorkerError(requestId, response);
    }

    // -- private methods --

    private void handle(Endpoint endpoint, Event event) {
        BfdLogicalPortFsm controller = controllerByEndpoint.get(endpoint);
        if (controller == null) {
            log.info("There is no BFD logical port controller on {} - ignore remove request", endpoint);
            return;
        }
        handle(controller, event);
    }

    private void handle(BfdLogicalPortFsm controller, Event event) {
        handle(controller, event, BfdLogicalPortFsmContext.EMPTY);
    }

    private void handle(BfdLogicalPortFsm controller, Event event, BfdLogicalPortFsmContext context) {
        BfdLogicalPortFsmFactory.EXECUTOR.fire(controller, event, context);
        if (controller.isTerminated()) {
            removeController(controller);
            log.info("Logical port controller {}(physical={}) has completed its job and been removed",
                    controller.getLogicalEndpoint(), controller.getPhysicalEndpoint().getPortNumber());
        }
    }

    private BfdLogicalPortFsm lookupController(Endpoint endpoint) {
        BfdLogicalPortFsm controller = controllerByEndpoint.get(endpoint);
        if (controller == null) {
            throw new BfdLogicalPortControllerNotFoundException(endpoint);
        }
        return controller;
    }

    private BfdLogicalPortFsm lookupControllerCreateIfMissing(Endpoint physical) {
        return lookupControllerCreateIfMissing(
                physical, physical.getPortNumber() + logicalPortNumberOffset);
    }

    private BfdLogicalPortFsm lookupControllerCreateIfMissing(Endpoint physical, int logicalPortNumber) {
        BfdLogicalPortFsm controller = controllerByEndpoint.get(physical);
        if (controller == null) {
            controller = createController(physical, logicalPortNumber);
        }
        return controller;
    }

    private BfdLogicalPortFsm createController(Endpoint physical, int logicalPortNumber) {
        BfdLogicalPortFsm controller = controllerFactory.produce(
                switchOnlineStatusMonitor, physical, logicalPortNumber);
        controllerByEndpoint.put(physical, controller);
        controllerByEndpoint.put(controller.getLogicalEndpoint(), controller);
        return controller;
    }

    private void removeController(BfdLogicalPortFsm controller) {
        final Endpoint logical = controller.getLogicalEndpoint();
        final Endpoint physical = controller.getPhysicalEndpoint();
        controllerByEndpoint.remove(logical);
        controllerByEndpoint.remove(physical);
        log.debug(
                "BFD logical port controller {} pysical-port={} have done it's jobs and been deleted",
                logical, physical.getPortNumber());
    }

    private void logServiceCall(String format, Object... arguments) {
        log.info("BFD-logical-port service request " + format, arguments);
    }
}
