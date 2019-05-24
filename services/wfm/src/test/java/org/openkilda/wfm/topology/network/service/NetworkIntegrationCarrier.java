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

import org.openkilda.messaging.command.reroute.RerouteFlows;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import lombok.Data;

@Data
public class NetworkIntegrationCarrier
        implements ISwitchCarrier, IPortCarrier, IBfdPortCarrier, IUniIslCarrier, IIslCarrier {
    private final NetworkSwitchService switchService;

    private final NetworkPortService portService;

    private final NetworkBfdPortService bfdPortService;

    private final NetworkUniIslService uniIslService;

    private final NetworkIslService islService;

    private ISwitchCarrier switchCarrier = this;
    private IPortCarrier portCarrier = this;
    private IBfdPortCarrier bfdPortCarrier = this;
    private IUniIslCarrier uniIslCarrier = this;
    private IIslCarrier islCarrier = this;

    public NetworkIntegrationCarrier(NetworkSwitchService switchService,
                                     NetworkPortService portService,
                                     NetworkBfdPortService bfdPortService,
                                     NetworkUniIslService uniIslService,
                                     NetworkIslService islService) {
        this.switchService = switchService;
        this.portService = portService;
        this.bfdPortService = bfdPortService;
        this.uniIslService = uniIslService;
        this.islService = islService;
    }

    @Override
    public void bfdEnableRequest(Endpoint physicalEndpoint, IslReference reference) {
        bfdPortService.enable(physicalEndpoint, reference);
    }

    @Override
    public void bfdDisableRequest(Endpoint physicalEndpoint) {
        bfdPortService.disable(physicalEndpoint);
    }

    @Override
    public void setupUniIslHandler(Endpoint endpoint, Isl history) {
        uniIslService.uniIslSetup(endpoint, history);
    }

    @Override
    public void enableDiscoveryPoll(Endpoint endpoint) {
        // WatchList service is not covered by this test
    }

    @Override
    public void disableDiscoveryPoll(Endpoint endpoint) {
        // WatchList service is not covered by this test
    }

    @Override
    public void notifyPortDiscovered(Endpoint endpoint, IslInfoData speakerDiscoveryEvent) {
        uniIslService.uniIslDiscovery(endpoint, speakerDiscoveryEvent);
    }

    @Override
    public void notifyPortDiscoveryFailed(Endpoint endpoint) {
        uniIslService.uniIslFail(endpoint);
    }

    @Override
    public void notifyPortPhysicalDown(Endpoint endpoint) {
        uniIslService.uniIslPhysicalDown(endpoint);
    }

    @Override
    public void removeUniIslHandler(Endpoint endpoint) {
        uniIslService.uniIslRemove(endpoint);
    }

    @Override
    public String setupBfdSession(NoviBfdSession bfdSession) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
        return "dummy";
    }

    @Override
    public String removeBfdSession(NoviBfdSession bfdSession) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
        return "dummy";
    }

    @Override
    public void bfdUpNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdUpDown(physicalEndpoint, true);
    }

    @Override
    public void bfdDownNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdUpDown(physicalEndpoint, false);
    }

    @Override
    public void bfdKillNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdKill(physicalEndpoint);
    }

    @Override
    public void setupPortHandler(Endpoint endpoint, Isl history) {
        portService.setup(endpoint, history);
    }

    @Override
    public void removePortHandler(Endpoint endpoint) {
        portService.remove(endpoint);
    }

    @Override
    public void setOnlineMode(Endpoint endpoint, boolean mode) {
        portService.updateOnlineMode(endpoint, mode);
    }

    @Override
    public void setPortLinkMode(Endpoint endpoint, LinkStatus linkStatus) {
        portService.updateLinkStatus(endpoint, linkStatus);
    }

    @Override
    public void setupBfdPortHandler(Endpoint endpoint, int physicalPortNumber) {
        bfdPortService.setup(endpoint, physicalPortNumber);
    }

    @Override
    public void removeBfdPortHandler(Endpoint logicalEndpoint) {
        bfdPortService.remove(logicalEndpoint);
    }

    @Override
    public void setBfdPortLinkMode(Endpoint logicalEndpoint, LinkStatus linkStatus) {
        bfdPortService.updateLinkStatus(logicalEndpoint, linkStatus);
    }

    @Override
    public void setBfdPortOnlineMode(Endpoint endpoint, boolean mode) {
        bfdPortService.updateOnlineMode(endpoint, mode);
    }

    @Override
    public void setupIslFromHistory(Endpoint endpoint, IslReference islReference, Isl history) {
        islService.islSetupFromHistory(endpoint, islReference, history);
    }

    @Override
    public void notifyIslUp(Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        islService.islUp(endpoint, reference, islData);
    }

    @Override
    public void notifyIslDown(Endpoint endpoint, IslReference reference, IslDownReason reason) {
        islService.islDown(endpoint, reference, reason);
    }

    @Override
    public void notifyIslMove(Endpoint endpoint, IslReference reference) {
        islService.islMove(endpoint, reference);
    }

    public void triggerReroute(RerouteFlows trigger) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }
}
