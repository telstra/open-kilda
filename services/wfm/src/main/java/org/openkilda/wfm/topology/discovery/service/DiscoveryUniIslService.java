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

import java.util.HashMap;
import java.util.Map;

public class DiscoveryUniIslService {
    private final Map<Endpoint, UniIslFsm> controller = new HashMap<>();

    private final FsmExecutor<UniIslFsm, UniIslFsmState, UniIslFsmEvent, UniIslFsmContext> controllerExecutor
            = UniIslFsm.makeExecutor();

    public void uniIslSetup(Endpoint endpoint, Isl history) {
        UniIslFsm uniIslFsm = UniIslFsm.create(endpoint, history);
        controller.put(endpoint, uniIslFsm);
    }

    /**
     * .
     */
    public void uniIslDiscovery(IUniIslCarrier carrier, Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        UniIslFsmContext context = new UniIslFsmContext(carrier);
        context.setDiscoveryEvent(speakerDiscoveryEvent);
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.DISCOVERY, context);
    }

    public void uniIslFail(IUniIslCarrier carrier, Endpoint endpoint) {
        UniIslFsmContext context = new UniIslFsmContext(carrier);
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.FAIL, context);
    }

    /**
     * .
     */
    public void uniIslPhysicalDown(IUniIslCarrier carrier, Endpoint endpoint) {
        UniIslFsmContext context = new UniIslFsmContext(carrier);
        controllerExecutor.fire(locateController(endpoint), UniIslFsmEvent.PHYSICAL_DOWN, context);
    }

    /**
     * .
     */
    public void uniIslBfdUpDown(IUniIslCarrier carrier, Endpoint endpoint, boolean isUp) {
        UniIslFsmContext context = new UniIslFsmContext(carrier);
        UniIslFsmEvent event = isUp ? UniIslFsmEvent.BFD_UP : UniIslFsmEvent.BFD_DOWN;
        controllerExecutor.fire(locateController(endpoint), event, context);
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
