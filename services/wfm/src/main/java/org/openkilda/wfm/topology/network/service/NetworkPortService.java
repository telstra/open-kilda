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
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.controller.port.PortFsm;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmContext;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmEvent;
import org.openkilda.wfm.topology.network.controller.port.PortFsm.PortFsmState;
import org.openkilda.wfm.topology.network.controller.port.PortReportFsm;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class NetworkPortService {
    private final PortFsm.PortFsmFactory controllerFactory;
    private final PortReportFsm.PortReportFsmFactory reportFactory;
    private final Map<Endpoint, PortFsm> controller = new HashMap<>();
    private final FsmExecutor<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> controllerExecutor;

    private final IPortCarrier carrier;

    public NetworkPortService(IPortCarrier carrier) {
        this(carrier, NetworkTopologyDashboardLogger.builder());
    }

    @VisibleForTesting
    NetworkPortService(IPortCarrier carrier, NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder) {
        this.carrier = carrier;

        controllerFactory = PortFsm.factory();
        controllerExecutor = controllerFactory.produceExecutor();

        reportFactory = PortReportFsm.factory(dashboardLoggerBuilder);
    }

    /**
     * .
     */
    public void setup(Endpoint endpoint, Isl history) {
        log.info("Port service receive setup request for {}", endpoint);
        // TODO: try to switch on atomic action i.e. port-setup + online|offline action in one event
        PortFsm portFsm = controllerFactory.produce(reportFactory, endpoint, history);
        controller.put(endpoint, portFsm);
    }

    /**
     * .
     */
    public void remove(Endpoint endpoint) {
        log.info("Port service receive remove request for {}", endpoint);
        PortFsm portFsm = controller.remove(endpoint);
        if (portFsm == null) {
            throw new IllegalStateException(String.format("Port FSM not found (%s).", endpoint));
        }
        PortFsmContext context = PortFsmContext.builder(carrier).build();
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
        log.debug("Port service receive online status change for {}, new status is {}", endpoint, event);
        controllerExecutor.fire(portFsm, event, PortFsmContext.builder(carrier).build());
    }

    /**
     * .
     */
    public void updateLinkStatus(Endpoint endpoint, LinkStatus status) {
        log.debug("Port service receive link status update for {} new status is {}", endpoint, status);
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
                        String.format("Unsupported %s value %s", LinkStatus.class.getName(), status));
        }
        controllerExecutor.fire(portFsm, event, PortFsmContext.builder(carrier).build());
    }

    /**
     * Feed port FSM with discovery event from discovery poll subsystem.
     */
    public void discovery(Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        log.debug("Port service receive discovery for {}: {}", endpoint, speakerDiscoveryEvent);

        PortFsm portFsm = locateController(endpoint);
        PortFsmContext context = PortFsmContext.builder(carrier)
                .speakerDiscoveryEvent(speakerDiscoveryEvent)
                .build();
        controllerExecutor.fire(portFsm, PortFsmEvent.DISCOVERY, context);
    }

    /**
     * Feed port FSM with fail event from discovery poll subsystem.
     */
    public void fail(Endpoint endpoint) {
        log.debug("Port service receive fail for {}", endpoint);

        PortFsm portFsm = locateController(endpoint);
        PortFsmContext context = PortFsmContext.builder(carrier).build();

        controllerExecutor.fire(portFsm, PortFsmEvent.FAIL, context);
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
