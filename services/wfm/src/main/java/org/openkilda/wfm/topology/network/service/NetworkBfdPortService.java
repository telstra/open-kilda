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

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm;
import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm.BfdPortFsmContext;
import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm.BfdPortFsmEvent;
import org.openkilda.wfm.topology.network.controller.bfd.BfdPortFsm.BfdPortFsmState;
import org.openkilda.wfm.topology.network.error.BfdPortControllerNotFoundException;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Slf4j
public class NetworkBfdPortService {
    private final IBfdPortCarrier carrier;
    private final PersistenceManager persistenceManager;

    private final Map<Endpoint, BfdPortFsm> controllerByPhysicalPort = new HashMap<>();
    private final Map<Endpoint, BfdPortFsm> controllerByLogicalPort = new HashMap<>();
    private final List<BfdPortFsm> pendingHousekeeping = new LinkedList<>();

    private final FsmExecutor<BfdPortFsm, BfdPortFsmState, BfdPortFsmEvent, BfdPortFsmContext> controllerExecutor
            = BfdPortFsm.makeExecutor();

    public NetworkBfdPortService(IBfdPortCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
    }

    /**
     * .
     */
    public void setup(Endpoint endpoint, int physicalPortNumber) {
        log.info("BFD-port service receive SETUP request for {} (physical-port:{})",
                  endpoint, physicalPortNumber);
        BfdPortFsm controller = BfdPortFsm.create(persistenceManager, endpoint, physicalPortNumber);

        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.HISTORY, context);

        controllerByLogicalPort.put(controller.getLogicalEndpoint(), controller);
        controllerByPhysicalPort.put(controller.getPhysicalEndpoint(), controller);
    }

    /**
     * Do BFD session remove (kill).
     */
    public Endpoint remove(Endpoint logicalEndpoint) {
        log.info("BFD-port service receive REMOVE request for {} (logical)", logicalEndpoint);

        BfdPortFsm controller = controllerByLogicalPort.remove(logicalEndpoint);
        if (controller == null) {
            throw BfdPortControllerNotFoundException.ofLogical(logicalEndpoint);
        }
        controllerByPhysicalPort.remove(controller.getPhysicalEndpoint());

        remove(controller);

        return controller.getPhysicalEndpoint();
    }

    private void remove(BfdPortFsm controller) {
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.KILL, context);

        if (controller.isHousekeeping()) {
            log.info("BFD-port {} (physical-port:{}) have switched into housekeeping mode",
                     controller.getLogicalEndpoint(), controller.getPhysicalEndpoint().getPortNumber());
            pendingHousekeeping.add(controller);
        } else {
            log.debug("BFD-port {} (physical-port:{}) do not require housekeeping, remove it immediately",
                      controller.getLogicalEndpoint(), controller.getPhysicalEndpoint().getPortNumber());
        }
    }

    /**
     * .
     */
    public void updateLinkStatus(Endpoint logicalEndpoint, LinkStatus linkStatus) {
        log.debug("BFD-port service receive logical port status update for {} (logical) status:{}",
                  logicalEndpoint, linkStatus);

        BfdPortFsm controller = lookupControllerByLogicalEndpoint(logicalEndpoint);
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();

        BfdPortFsmEvent event;
        switch (linkStatus) {
            case UP:
                event = BfdPortFsmEvent.PORT_UP;
                break;
            case DOWN:
                event = BfdPortFsmEvent.PORT_DOWN;
                break;
            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported %s.%s link state. Can\'t handle event for %s",
                        LinkStatus.class.getName(), linkStatus, logicalEndpoint));
        }
        controllerExecutor.fire(controller, event, context);
    }

    /**
     * Handle change in ONLINE status of switch that own logical-BFD port.
     */
    public void updateOnlineMode(Endpoint endpoint, boolean mode) {
        BfdPortFsmEvent event = mode ? BfdPortFsmEvent.ONLINE : BfdPortFsmEvent.OFFLINE;
        log.debug("BFD-port service receive online mode change notification for {} (logical) mode:{}",
                  endpoint, event);

        BfdPortFsm controller = lookupControllerByLogicalEndpoint(endpoint);
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();
        controllerExecutor.fire(controller, event, context);
    }

    /**
     * .
     */
    public void enable(Endpoint physicalEndpoint, IslReference reference) {
        log.info("BFD-port service receive ENABLE request for {} (physical)", physicalEndpoint);

        BfdPortFsm controller = lookupControllerByPhysicalEndpoint(physicalEndpoint);
        log.info("Setup BFD session request for {} (logical-port:{})",
                 controller.getPhysicalEndpoint(), controller.getLogicalEndpoint().getPortNumber());
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier)
                .islReference(reference)
                .build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.ENABLE, context);
    }

    /**
     * .
     */
    public void disable(Endpoint physicalEndpoint) {
        log.info("BFD-port service receive DISABLE request for {} (physical)", physicalEndpoint);

        BfdPortFsm controller = lookupControllerByPhysicalEndpoint(physicalEndpoint);
        log.info("Remove BFD session request for {} (logical-port:{})",
                 controller.getPhysicalEndpoint(), controller.getLogicalEndpoint().getPortNumber());
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.DISABLE, context);
    }

    /**
     * Handle speaker response.
     */
    public void speakerResponse(String key, Endpoint logicalEndpoint, BfdSessionResponse response) {
        log.debug("BFD-port service receive speaker response on BFD-session-setup request for {} (logical) key:{}",
                  logicalEndpoint, key);

        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier)
                .requestKey(key)
                .bfdSessionResponse(response)
                .build();
        handleSpeakerResponse(logicalEndpoint, context);
    }

    /**
     * Handle speaker timeout response.
     */
    public void speakerTimeout(String key, Endpoint logicalEndpoint) {
        log.debug("BFD-port service receive speaker timeout response for {} (logical) key:{}", logicalEndpoint, key);

        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier)
                .requestKey(key)
                .build();
        handleSpeakerResponse(logicalEndpoint, context);
    }

    private void handleSpeakerResponse(Endpoint logicalEndpoint, BfdPortFsmContext context) {
        BfdPortFsm controller = controllerByLogicalPort.get(logicalEndpoint);
        if (controller != null) {
            controllerExecutor.fire(controller, BfdPortFsmEvent.SPEAKER_RESPONSE, context);
        }

        handleSpeakerResponse(context);
    }

    private void handleSpeakerResponse(BfdPortFsmContext context) {
        Iterator<BfdPortFsm> iter;
        for (iter = pendingHousekeeping.iterator(); iter.hasNext(); ) {
            BfdPortFsm controller = iter.next();
            controllerExecutor.fire(controller, BfdPortFsmEvent.SPEAKER_RESPONSE, context);

            if (!controller.isHousekeeping()) {
                log.info("BFD-port {} (physical-port:{}) have done with housekeeping, remove it",
                         controller.getLogicalEndpoint(), controller.getPhysicalEndpoint().getPortNumber());
                iter.remove();
            }
        }
    }

    // -- private --
    private BfdPortFsm lookupControllerByPhysicalEndpoint(Endpoint endpoint) {
        BfdPortFsm controller = controllerByPhysicalPort.get(endpoint);
        if (controller == null) {
            throw BfdPortControllerNotFoundException.ofPhysical(endpoint);
        }
        return controller;
    }

    private BfdPortFsm lookupControllerByLogicalEndpoint(Endpoint endpoint) {
        BfdPortFsm controller = controllerByLogicalPort.get(endpoint);
        if (controller == null) {
            throw BfdPortControllerNotFoundException.ofLogical(endpoint);
        }
        return controller;
    }
}
