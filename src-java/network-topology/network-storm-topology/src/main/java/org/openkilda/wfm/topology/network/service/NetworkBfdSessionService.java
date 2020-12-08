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
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.controller.bfd.BfdSessionController;
import org.openkilda.wfm.topology.network.controller.bfd.BfdSessionFsm;
import org.openkilda.wfm.topology.network.error.BfdSessionControllerNotFoundException;
import org.openkilda.wfm.topology.network.model.BfdSessionData;
import org.openkilda.wfm.topology.network.utils.EndpointStatusMonitor;
import org.openkilda.wfm.topology.network.utils.SwitchOnlineStatusMonitor;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NetworkBfdSessionService {
    private final BfdSessionFsm.BfdSessionFsmFactory fsmFactory;

    private final Map<Endpoint, BfdSessionController> controllerByEndpoint = new HashMap<>();

    public NetworkBfdSessionService(
            PersistenceManager persistenceManager, SwitchOnlineStatusMonitor switchOnlineStatusMonitor,
            EndpointStatusMonitor endpointStatusMonitor, IBfdSessionCarrier carrier) {
        fsmFactory = BfdSessionFsm.factory(
                persistenceManager, switchOnlineStatusMonitor, endpointStatusMonitor, carrier);
    }

    /**
     * Enable (create new) or update properties of existing BFD session.
     */
    public void enableUpdate(Endpoint logical, int physicalPortNumber, BfdSessionData sessionData) {
        log.info(
                "BFD-session service receive ENABLE/UPDATE request for {} (logical) for {} with properties {}",
                logical, sessionData.getReference(), sessionData.getProperties());

        BfdSessionController entry = controllerByEndpoint.get(logical);
        if (entry == null) {
            entry = makeController(logical, physicalPortNumber);
        }
        entry.enableUpdate(sessionData);
    }

    /**
     * Disable existing BFD session.
     */
    public void disable(Endpoint logical) {
        log.info("BFD-session service receive DISABLE request for {}", logical);
        lookupController(logical).disable();
    }

    /**
     * Handle speaker response.
     */
    public void speakerResponse(Endpoint logical, String key, BfdSessionResponse response) {
        log.debug("BFD-session service receive speaker response for {} (logical) key:{}",
                  logical, key);
        lookupController(logical).speakerResponse(key, response);
    }

    /**
     * Handle speaker timeout response.
     */
    public void speakerTimeout(Endpoint logical, String key) {
        log.debug("BFD-session service receive speaker timeout response for {} (logical) key:{}", logical, key);
        lookupController(logical).speakerResponse(key);
    }

    public void rotate(Endpoint logical, boolean error) {
        log.debug("BFD-session service rotate request for {} (logical) error:{}", logical, error);
        lookupController(logical).handleCompleteNotification(error);
    }

    /**
     * Handle BFD controller complete notification.
     */
    public void sessionCompleteNotification(Endpoint physical) {
        BfdSessionController controller = controllerByEndpoint.remove(physical);
        if (controller != null) {
            controllerByEndpoint.remove(controller.getLogical());
        }
    }

    // -- private --

    private BfdSessionController makeController(Endpoint logical, int physicalPortNumber) {
        BfdSessionController controller = new BfdSessionController(fsmFactory, logical, physicalPortNumber);
        controllerByEndpoint.put(logical, controller);
        controllerByEndpoint.put(Endpoint.of(logical.getDatapath(), physicalPortNumber), controller);
        return controller;
    }

    private BfdSessionController lookupController(Endpoint endpoint) {
        BfdSessionController controller = controllerByEndpoint.get(endpoint);
        if (controller == null) {
            throw new BfdSessionControllerNotFoundException(endpoint);
        }
        return controller;
    }
}
