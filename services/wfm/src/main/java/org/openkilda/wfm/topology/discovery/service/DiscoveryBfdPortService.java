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
import org.openkilda.wfm.share.hubandspoke.IKeyFactory;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsm;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsmContext;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.BfdPortFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryBfdPortService {
    private final PersistenceManager persistenceManager;
    private final IKeyFactory requestKeyFactory;

    private final Map<Endpoint, BfdPortFsm> controllerByPhysicalPort = new HashMap<>();
    private final Map<Endpoint, BfdPortFsm> controllerByLogicalPort = new HashMap<>();
    private final Map<String, Endpoint> speakerRequests = new HashMap<>();

    private final FsmExecutor<BfdPortFsm, BfdPortFsmState, BfdPortFsmEvent, BfdPortFsmContext> controllerExecutor
            = BfdPortFsm.makeExecutor();

    public DiscoveryBfdPortService(PersistenceManager persistenceManager, IKeyFactory requestKeyFactory) {
        this.persistenceManager = persistenceManager;
        this.requestKeyFactory = requestKeyFactory;
    }

    /**
     * .
     */
    public void setup(IBfdPortCarrier carrier, BfdPortFacts portFacts) {
        log.debug("Setup BFD-port {}", portFacts.getEndpoint());
        BfdPortFsm controller = BfdPortFsm.create(persistenceManager, portFacts);

        // TODO load exising discrimanator for this port/session from persistent storage if any
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier)
                .portFacts(portFacts)
                .build();
        controllerExecutor.fire(controller, BfdPortFsmEvent.HISTORY, context);

        controllerByLogicalPort.put(controller.getLogicalEndpoint(), controller);
        controllerByPhysicalPort.put(controller.getPhysicalEndpoint(), controller);
    }

    /**
     * .
     */
    public void remove(IBfdPortCarrier carrier, Endpoint logicalEndpoint) {
        log.debug("Remove BFD-port {}", logicalEndpoint);
        BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();

        BfdPortFsm controller = controllerByLogicalPort.remove(logicalEndpoint);
        if (controller != null) {
            controllerExecutor.fire(controller, BfdPortFsmEvent.KILL, context);
            controllerByPhysicalPort.remove(controller.getPhysicalEndpoint());
        } else {
            logMissingControllerByLogicalEndpoint(logicalEndpoint, "remove handler");
        }
    }

    /**
     * .
     */
    public void updateLinkStatus(IBfdPortCarrier carrier, PortFacts logicalPortFacts) {
        log.debug("BFD status on {} become {}", logicalPortFacts.getEndpoint(), logicalPortFacts.getLinkStatus());
        BfdPortFsm controller = controllerByLogicalPort.get(logicalPortFacts.getEndpoint());
        if (controller != null) {
            BfdPortFsmContext context = BfdPortFsmContext.builder(carrier).build();
            BfdPortFsmEvent event;

            switch (logicalPortFacts.getLinkStatus()) {
                case UP:
                    event = BfdPortFsmEvent.PORT_UP;
                    break;
                case DOWN:
                    event = BfdPortFsmEvent.PORT_DOWN;
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported %s.%s link state. Can\'t handle event for %s",
                            PortFacts.LinkStatus.class.getName(), logicalPortFacts.getLinkStatus(),
                            logicalPortFacts.getEndpoint()));
            }
            controllerExecutor.fire(controller, event, context);
        } else {
            logMissingControllerByLogicalEndpoint(
                    logicalPortFacts.getEndpoint(),
                    String.format("handle link status change to %s", logicalPortFacts.getLinkStatus()));
        }
    }

    public void updateOnlineMode(IBfdPortCarrier carrier, Endpoint endpoint, boolean mode) {
        log.debug("BFD port {} become {} (due to switch availability change)", endpoint, mode ? "ONLINE" : "OFFLINE");
        // Current implementation do not take into account switch's online status
    }

    /**
     * .
     */
    public void handleEnableRequest(IBfdPortCarrier carrier, Endpoint physicalEndpoint, IslReference reference) {
        BfdPortFsm controller = controllerByPhysicalPort.get(physicalEndpoint);
        if (controller != null) {
            log.debug("BFD port {} => {} receive ISL discovery confirmation",
                      controller.getLogicalEndpoint(), controller.getPhysicalEndpoint().getPortNumber());
            BfdPortFsmContext context = BfdPortFsmContext.builder(carrier)
                    .requestKeyFactory(new RequestTracer(this, requestKeyFactory, physicalEndpoint))
                    .islReference(reference)
                    .build();
            controllerExecutor.fire(controller, BfdPortFsmEvent.BI_ISL_UP, context);
        } else {
            logMissingControllerByPhysicalEndpoint(physicalEndpoint, "handle ISL up notification");
        }
    }

    public void handleDisableRequest(IBfdPortCarrier carrier, Endpoint physicalEndpoint, IslReference reference) {
        // TODO
    }

    public void speakerReponse(IBfdPortCarrier carrier, Endpoint logicalEndpoint, BfdSessionResponse response) {
        // TODO
    }

    public void speakerTimeout(IBfdPortCarrier carrier, Endpoint logicalEndpoint) {
        // TODO
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

    private void traceSpeakerRequest(String key, Endpoint endpoint) {
        speakerRequests.put(key, endpoint);
    }

    /**
     * .
     */
    public final class RequestTracer implements IKeyFactory {
        private final DiscoveryBfdPortService service;
        private final IKeyFactory keyFactory;
        private final Endpoint endpoint;

        private RequestTracer(DiscoveryBfdPortService service, IKeyFactory keyFactory, Endpoint endpoint) {
            this.service = service;
            this.keyFactory = keyFactory;
            this.endpoint = endpoint;
        }

        /**
         * .
         */
        public String next() {
            String key = keyFactory.next();
            service.traceSpeakerRequest(key, endpoint);
            return key;
        }
    }
}
