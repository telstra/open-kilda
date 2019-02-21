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
import org.openkilda.wfm.topology.discovery.controller.IslFsmContext;
import org.openkilda.wfm.topology.discovery.controller.IslFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.IslFsmState;
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

    public DiscoveryIslService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public void islSetupFromHistory(IIslCarrier carrier, Endpoint endpoint, IslReference reference, Isl history) {
        // TODO
    }

    /**
     * .
     */
    public void islUp(IIslCarrier carrier, Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        log.debug("ISL discovery {} (on {})", reference, endpoint);
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
        log.debug("ISL fail {} (on {})", reference, endpoint);
        IslFsm islFsm = locateControllerCreateIfAbsent(reference);
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint)
                .physicalLinkDown(isPhysicalDown)
                .build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_DOWN, context);
    }

    /**
     * .
     */
    public void islMove(IIslCarrier carrier, Endpoint endpoint, IslReference reference) {
        log.debug("ISL fail(moved) {} (on {})", reference, endpoint);
        IslFsm islFsm = locateControllerCreateIfAbsent(reference);
        IslFsmContext context = IslFsmContext.builder(carrier, endpoint).build();
        controllerExecutor.fire(islFsm, IslFsmEvent.ISL_MOVE, context);
    }

    // -- private --

    private IslFsm locateControllerCreateIfAbsent(IslReference reference) {
        return controller.computeIfAbsent(reference, key -> IslFsm.create(persistenceManager, reference));
    }

    public void bfdEvent(IIslCarrier carrier, IslBfdFlagUpdated payload) {
        // TODO: reaction on bfd_enable from NB
    }
}
