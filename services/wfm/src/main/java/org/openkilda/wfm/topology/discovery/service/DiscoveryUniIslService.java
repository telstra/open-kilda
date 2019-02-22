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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.UniIslFsm;
import org.openkilda.wfm.topology.discovery.controller.UniIslFsmContext;
import org.openkilda.wfm.topology.discovery.controller.UniIslFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.UniIslFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryUniIslService {
    private final Map<Endpoint, UniIslFsm> controller = new HashMap<>();

    private final FsmExecutor<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> controllerExecutor
            = UniIslFsm.makeExecutor();

    private final IUniIslCarrier carrier;

    public DiscoveryUniIslService(IUniIslCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * .
     */
    public void uniIslSetup(Endpoint endpoint, Isl history) {
        log.debug("Setup uni-ISL on {}", endpoint);
        UniIslFsm fsm = UniIslFsm.create(endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier)
                .history(history)
                .build();
        controllerExecutor.fire(fsm, UniIslFsmEvent.ACTIVATE, context);
        controller.put(endpoint, fsm);
    }

    /**
     * .
     */
    public void uniIslDiscovery(Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        log.debug("ISL on {} become discovered (uni-ISL view)", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier)
                .discoveryEvent(speakerDiscoveryEvent)
                .build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.DISCOVERY, context);
    }

    /**
     * .
     */
    public void uniIslFail(Endpoint endpoint) {
        log.debug("ISL on {} become failed (uni-ISL view)", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.FAIL, context);
    }

    /**
     * .
     */
    public void uniIslPhysicalDown(Endpoint endpoint) {
        log.debug("ISL on {} become failed (link DOWN) (uni-ISL view)", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.PHYSICAL_DOWN, context);
    }

    /**
     * .
     */
    public void uniIslBfdUpDown(Endpoint endpoint, boolean isUp) {
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        UniIslFsmEvent event = isUp ? UniIslFsmEvent.BFD_UP : UniIslFsmEvent.BFD_DOWN;
        log.debug("Receive BFD link update {} on {} (uni-ISL view)", event, endpoint);
        controllerExecutor.fire(locateController(endpoint), event, context);
    }

    /**
     * .
     */
    public void uniIslRemove(Endpoint endpoint) {
        log.debug("Receive UniIsl kill request for {}", endpoint);
        controller.remove(endpoint);
    }

    /**
     * .
     */
    public void uniIslBfdKill(Endpoint endpoint) {
        log.debug("Receive UniIsl bfd-kill request for {}", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.BFD_KILL, context);
    }



    // -- private --

    private UniIslFsm locateController(Endpoint endpoint) {
        UniIslFsm uniIslFsm = controller.get(endpoint);
        if (uniIslFsm == null) {
            throw new IllegalStateException(String.format("Uni-ISL FSM not found (%s).", endpoint));
        }
        return uniIslFsm;
    }
}
