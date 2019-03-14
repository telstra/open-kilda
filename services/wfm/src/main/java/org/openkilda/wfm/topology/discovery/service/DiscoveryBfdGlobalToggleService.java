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

import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.BfdGlobalToggleFsm;
import org.openkilda.wfm.topology.discovery.error.BfdGlobalToggleControllerNotFoundException;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.LinkStatus;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryBfdGlobalToggleService {
    private final IBfdGlobalToggleCarrier carrier;

    private final FeatureTogglesRepository featureTogglesRepository;

    private final Map<Endpoint, BfdGlobalToggleFsm> controllerByEndpoint = new HashMap<>();

    private final FsmExecutor<BfdGlobalToggleFsm, BfdGlobalToggleFsm.BfdGlobalToggleFsmState,
            BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent, BfdGlobalToggleFsm.BfdGlobalToggleFsmContext> controllerExecutor
            = BfdGlobalToggleFsm.makeExecutor();

    public DiscoveryBfdGlobalToggleService(IBfdGlobalToggleCarrier carrier, PersistenceManager persistenceManager) {
        this.carrier = carrier;

        this.featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
    }

    /**
     * Setup global BFD-toggle controller.
     */
    public void setup(Endpoint endpoint) {
        log.debug("BFD global toggle service receive setup request for {}", endpoint);
        if (controllerByEndpoint.containsKey(endpoint)) {
            throw new IllegalArgumentException(String.format(
                    "Receive BFD-global-toggle SETUP request for %s, but this controller already exists (do not replace"
                            + " existing handler)", endpoint));
        }

        BfdGlobalToggleFsm fsm = BfdGlobalToggleFsm.create(carrier, endpoint, featureTogglesRepository);
        controllerByEndpoint.put(endpoint, fsm);
    }

    /**
     * Remove global BFD-toggle controller.
     */
    public void remove(Endpoint endpoint) {
        BfdGlobalToggleFsm controller = controllerByEndpoint.remove(endpoint);
        if (controller == null) {
            throw new IllegalArgumentException(String.format(
                    "Unable to remove BFD-global-toggle controller for %s - there is no such controller", endpoint));
        }

        BfdGlobalToggleFsm.BfdGlobalToggleFsmContext context = BfdGlobalToggleFsm.BfdGlobalToggleFsmContext.builder()
                .build();
        controllerExecutor.fire(controller, BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent.KILL, context);
    }

    /**
     * Consume feature toggles update notification.
     */
    public void toggleUpdate(FeatureToggles toggles) {
        log.debug("BFD global toggle service receive toggles update notification: {}", toggles);

        Boolean value = toggles.getUseBfdForIslIntegrityCheck();
        if (value == null) {
            // ignore null value
            log.debug("Ignore global BFD toggle update, due missing toggle value (null)");
            return;
        }

        BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent event;
        if (value) {
            event = BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent.ENABLE;
        } else {
            event = BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent.DISABLE;
        }

        BfdGlobalToggleFsm.BfdGlobalToggleFsmContext context = BfdGlobalToggleFsm.BfdGlobalToggleFsmContext.builder()
                .build();
        for (BfdGlobalToggleFsm controller : controllerByEndpoint.values()) {
            controllerExecutor.fire(controller, event, context);
        }
    }

    /**
     * Consume BFD status change notifications.
     */
    public void bfdStateChange(Endpoint physicalEndpoint, LinkStatus status) {
        log.debug("BFD global toggle service receive BFD status change for {} new-status:{}", physicalEndpoint, status);

        BfdGlobalToggleFsm controller = lookupController(physicalEndpoint);
        BfdGlobalToggleFsm.BfdGlobalToggleFsmContext context = BfdGlobalToggleFsm.BfdGlobalToggleFsmContext.builder()
                .build();
        BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent event;
        switch (status) {
            case UP:
                event = BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent.BFD_UP;
                break;
            case DOWN:
                event = BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent.BFD_DOWN;
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported %s value %s", LinkStatus.class.getName(), status));
        }
        controllerExecutor.fire(controller, event, context);
    }

    /**
     * Consume BFD-kill notification.
     */
    public void bfdKillNotification(Endpoint physicalEndpoint) {
        log.debug("BFD global toggle service receive BFD-kill notification");

        BfdGlobalToggleFsm controller = lookupController(physicalEndpoint);
        BfdGlobalToggleFsm.BfdGlobalToggleFsmContext context = BfdGlobalToggleFsm.BfdGlobalToggleFsmContext.builder()
                .build();
        controllerExecutor.fire(controller, BfdGlobalToggleFsm.BfdGlobalToggleFsmEvent.BFD_KILL, context);
    }

    private BfdGlobalToggleFsm lookupController(Endpoint endpoint) {
        BfdGlobalToggleFsm controller = controllerByEndpoint.get(endpoint);
        if (controller == null) {
            throw new BfdGlobalToggleControllerNotFoundException(endpoint);
        }
        return controller;
    }
}
