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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.Isl;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.network.controller.UniIslFsm;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmContext;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmEvent;
import org.openkilda.wfm.topology.network.controller.UniIslFsm.UniIslFsmState;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NetworkUniIslService {
    private final Map<Endpoint, UniIslFsm> controller = new HashMap<>();

    private final FsmExecutor<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> controllerExecutor
            = UniIslFsm.makeExecutor();

    private final IUniIslCarrier carrier;

    public NetworkUniIslService(IUniIslCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * .
     */
    public void uniIslSetup(Endpoint endpoint, Isl history) {
        log.info("Uni-ISL service receive SETUP request for {}", endpoint);
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
        log.debug("Uni-ISL service receive DISCOVERED notification for {}", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier)
                .discoveryEvent(speakerDiscoveryEvent)
                .build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.DISCOVERY, context);
    }

    /**
     * .
     */
    public void uniIslFail(Endpoint endpoint) {
        log.debug("Uni-ISL service receive FAILED notification for {}", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.FAIL, context);
    }

    /**
     * .
     */
    public void uniIslPhysicalDown(Endpoint endpoint) {
        log.debug("Uni-ISL service receive PHYSICAL-DOWN notification for {}", endpoint);
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.PHYSICAL_DOWN, context);
    }

    /**
     * .
     */
    public void uniIslBfdUpDown(Endpoint endpoint, boolean isUp) {
        UniIslFsmContext context = UniIslFsmContext.builder(carrier).build();
        UniIslFsmEvent event = isUp ? UniIslFsmEvent.BFD_UP : UniIslFsmEvent.BFD_DOWN;
        log.debug("Uni-ISL service receive BFD status update for {} - status:{}", endpoint, event);
        controllerExecutor.fire(locateController(endpoint), event, context);
    }

    /**
     * .
     */
    public void uniIslRemove(Endpoint endpoint) {
        log.info("Uni-ISL service receive KILL request for {}", endpoint);
        controller.remove(endpoint);
    }

    /**
     * .
     */
    public void uniIslBfdKill(Endpoint endpoint) {
        log.debug("Uni-ISL service receive BFD-KILL notification for {}", endpoint);
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
