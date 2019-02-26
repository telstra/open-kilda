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

import org.openkilda.model.Isl;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.PortFsm;
import org.openkilda.wfm.topology.discovery.controller.PortFsmContext;
import org.openkilda.wfm.topology.discovery.controller.PortFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.PortFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DiscoveryPortService {
    private final Map<Endpoint, PortFsm> controller = new HashMap<>();
    private final FsmExecutor<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> controllerExecutor
            = PortFsm.makeExecutor();

    private final IPortCarrier carrier;

    public DiscoveryPortService(IPortCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * .
     */
    public void setup(PortFacts portFacts, Isl history) {
        log.debug("Setup port {}", portFacts.getEndpoint());
        Endpoint endpoint = portFacts.getEndpoint();
        PortFsm portFsm = PortFsm.create(endpoint, history);
        controller.put(endpoint, portFsm);
    }

    /**
     * .
     */
    public void remove(Endpoint endpoint) {
        log.debug("Remove port {}", endpoint);
        PortFsm portFsm = controller.remove(endpoint);
        if (portFsm == null) {
            throw new IllegalStateException(String.format("Port FSM not found (%s).", endpoint));
        }
        PortFsmContext context = new PortFsmContext(carrier);
        controllerExecutor.fire(portFsm, PortFsmEvent.PORT_DEL, context);
    }

    /**
     * .
     */
    public void updateOnlineMode(Endpoint endpoint, boolean online) {
        PortFsm portFsm = locateController(endpoint);
        PortFsmEvent event;
        if (online) {
            event = PortFsmEvent.ONLINE;
        } else {
            event = PortFsmEvent.OFFLINE;
        }
        log.debug("Set port {} online mode to {}", endpoint, event);
        controllerExecutor.fire(portFsm, event, new PortFsmContext(carrier));
    }

    /**
     * .
     */
    public void updateLinkStatus(Endpoint endpoint, PortFacts.LinkStatus status) {
        PortFsm portFsm = locateController(endpoint);
        PortFsmEvent event;
        switch (status) {
            case UP:
                event = PortFsmEvent.PORT_UP;
                break;
            case DOWN:
                event = PortFsmEvent.PORT_DOWN;
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported %s value %s", PortFacts.LinkStatus.class.getName(), status));
        }
        log.debug("Physical port {} become {}", endpoint, event);
        controllerExecutor.fire(portFsm, event, new PortFsmContext(carrier));
    }

    // -- private --

    private PortFsm locateController(Endpoint endpoint) {
        PortFsm portFsm = controller.get(endpoint);
        if (portFsm == null) {
            throw new IllegalStateException(String.format("Port FSM not found (%s).", endpoint));
        }
        return portFsm;
    }
}
