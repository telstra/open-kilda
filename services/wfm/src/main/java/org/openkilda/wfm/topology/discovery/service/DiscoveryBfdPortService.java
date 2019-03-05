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

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsm;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsm.BfdPortFsmContext;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsm.BfdPortFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsm.BfdPortFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.LinkStatus;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryBfdPortService {
    private final IBfdPortCarrier carrier;
    private final PersistenceManager persistenceManager;

    private final Map<Endpoint, BfdPortFsm> controllerByPhysicalPort = new HashMap<>();
    private final Map<Endpoint, BfdPortFsm> controllerByLogicalPort = new HashMap<>();

    private final FsmExecutor<BfdPortFsm, BfdPortFsmState, BfdPortFsmEvent, BfdPortFsmContext> controllerExecutor
            = BfdPortFsm.makeExecutor();

    public DiscoveryBfdPortService(IBfdPortCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;
        this.persistenceManager = persistenceManager;
    }

    /**
     * .
     */
    public void setup(BfdPortFacts portFacts) {
        log.debug("BFD-port service receive SETUP request for logical-port {} (physical-port:{})",
                  portFacts.getEndpoint(), portFacts.getPhysicalPortNumber());
        BfdPortFsm controller = BfdPortFsm.create(persistenceManager, portFacts);

        BfdPortFsmContext context = BfdPortFsmContext.builder(controller, carrier).build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.HISTORY, context);

        controllerByLogicalPort.put(controller.getLogicalEndpoint(), controller);
        controllerByPhysicalPort.put(controller.getPhysicalEndpoint(), controller);
    }

    /**
     * .
     */
    public void remove(Endpoint logicalEndpoint) {
        log.debug("BFD-port service receive REMOVE request for logical-port {}", logicalEndpoint);

        BfdPortFsm controller = controllerByLogicalPort.remove(logicalEndpoint);
        if (controller != null) {
            BfdPortFsmContext context = BfdPortFsmContext.builder(controller, carrier).build();
            controllerExecutor.fire(controller, BfdPortFsmEvent.KILL, context);
            controllerByPhysicalPort.remove(controller.getPhysicalEndpoint());
        } else {
            logMissingControllerByLogicalEndpoint(logicalEndpoint, "remove handler");
        }
    }

    /**
     * .
     */
    public void updateLinkStatus(Endpoint logicalEndpoint, LinkStatus linkStatus) {
        log.debug("BFD-port service receive logical port status update for logical-port {} status:{}",
                  logicalEndpoint, linkStatus);

        BfdPortFsm controller = controllerByLogicalPort.get(logicalEndpoint);
        if (controller != null) {
            BfdPortFsmContext context = BfdPortFsmContext.builder(controller, carrier).build();
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
        } else {
            logMissingControllerByLogicalEndpoint(
                    logicalEndpoint, String.format("handle link status change to %s", linkStatus));
        }
    }

    /**
     * Handle change in ONLINE status of switch that own logical-BFD port.
     */
    public void updateOnlineMode(Endpoint endpoint, boolean mode) {
        log.debug("BFD-port service receive online mode change notification for logical-port {} mode:{}",
                  endpoint, mode ? "ONLINE" : "OFFLINE");
        // Current implementation do not take into account switch's online status
    }

    /**
     * .
     */
    public void enable(Endpoint physicalEndpoint, IslReference reference) {
        log.debug("BFD-port service receive ENABLE request for physical-port {}", physicalEndpoint);
        BfdPortFsm controller = controllerByPhysicalPort.get(physicalEndpoint);
        if (controller != null) {
            log.info("Setup BFD session request for %s (logical-port:{})",
                     controller.getPhysicalEndpoint(), controller.getLogicalEndpoint().getPortNumber());
            BfdPortFsmContext context = BfdPortFsmContext.builder(controller, carrier)
                    .islReference(reference)
                    .build();
            controllerExecutor.fire(controller, BfdPortFsmEvent.ENABLE, context);
        } else {
            logMissingControllerByPhysicalEndpoint(physicalEndpoint, "handle ISL up notification");
        }
    }

    public void disable(Endpoint physicalEndpoint, IslReference reference) {
        // TODO
    }

    /**
     * Handle speaker response.
     */
    public void speakerResponse(Endpoint logicalEndpoint, BfdSessionResponse response) {
        log.debug("BFD-port service receive speaker response on BFD-session-setup request for {}", logicalEndpoint);
        BfdPortFsm controller = controllerByLogicalPort.get(logicalEndpoint);
        if (controller != null) {
            speakerResponse(controller, response);
        } else {
            logMissingControllerByLogicalEndpoint(logicalEndpoint, "handle speaker response");
        }
    }

    private void speakerResponse(BfdPortFsm controller, BfdSessionResponse response) {
        BfdPortFsmEvent event;
        if (response.getErrorCode() == null) {
            event = BfdPortFsmEvent.SPEAKER_SUCCESS;
        } else {
            event = BfdPortFsmEvent.SPEAKER_FAIL;
        }

        BfdPortFsmContext context = BfdPortFsmContext.builder(controller, carrier)
                .bfdSessionResponse(response)
                .build();
        controllerExecutor.fire(controller, event, context);
    }

    /**
     * Handle speaker timeout response.
     */
    public void speakerTimeout(String key, Endpoint logicalEndpoint) {
        log.debug("BFD-port service receive speaker timeout response for {}", logicalEndpoint);
        BfdPortFsm controller = controllerByLogicalPort.get(logicalEndpoint);
        if (controller != null) {
            speakerTimeout(controller, key);
        } else {
            logMissingControllerByLogicalEndpoint(logicalEndpoint, "handle speaker timeout");
        }
    }

    private void speakerTimeout(BfdPortFsm controller, String key) {
        BfdPortFsmContext context = BfdPortFsmContext.builder(controller, carrier)
                .requestKey(key)
                .build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.SPEAKER_FAIL, context);
    }

    // -- private --
    private void logMissingControllerByLogicalEndpoint(Endpoint endpoint, String operation) {
        logMissingController(String.format("logical endpoint %s", endpoint), operation);
    }

    private void logMissingControllerByPhysicalEndpoint(Endpoint endpoint, String operation) {
        logMissingController(String.format("physical endpoint %s", endpoint), operation);
    }

    private void logMissingController(String endpoint, String operation) {
        log.error("There is no BFD handler associated with {} - unable to {}", endpoint, operation);
    }
}
