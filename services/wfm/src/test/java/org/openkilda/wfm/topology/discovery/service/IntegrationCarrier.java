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

import org.openkilda.messaging.command.reroute.RerouteFlows;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.model.Isl;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslDataHolder;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;

import lombok.Data;

@Data
public class IntegrationCarrier implements ISwitchCarrier, IPortCarrier, IBfdPortCarrier, IUniIslCarrier, IIslCarrier {
    private final DiscoverySwitchService switchService;

    private final DiscoveryPortService portService;

    private final DiscoveryBfdPortService bfdPortService;

    private final DiscoveryUniIslService uniIslService;

    private final DiscoveryIslService islService;

    private ISwitchCarrier switchCarrier = this;
    private IPortCarrier portCarrier = this;
    private IBfdPortCarrier bfdPortCarrier = this;
    private IUniIslCarrier uniIslCarrier = this;
    private IIslCarrier islCarrier = this;

    public IntegrationCarrier(DiscoverySwitchService switchService,
                              DiscoveryPortService portService,
                              DiscoveryBfdPortService bfdPortService,
                              DiscoveryUniIslService uniIslService,
                              DiscoveryIslService islService) {
        this.switchService = switchService;
        this.portService = portService;
        this.bfdPortService = bfdPortService;
        this.uniIslService = uniIslService;
        this.islService = islService;
    }

    @Override
    public void notifyBiIslUp(Endpoint physicalEndpoint, IslReference reference) {
        bfdPortService.biIslBecomeUp(bfdPortCarrier, physicalEndpoint, reference);
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
    public void notifyPortPhysicalDown(Endpoint endpoint) {
        uniIslService.uniIslPhysicalDown(uniIslCarrier, endpoint);
    }

    @Override
    public void removeUniIslHandler(Endpoint endpoint) {
        uniIslService.uniIslRemove(endpoint);
    }

    public void setupBfdSession(String requestKey, NoviBfdSession bfdSession) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    @Override
    public void setupPortHandler(PortFacts portFacts, Isl history) {
        portService.setup(portFacts, history);
    }

    @Override
    public void removePortHandler(Endpoint endpoint) {
        portService.remove(portCarrier, endpoint);
    }

    @Override
    public void setOnlineMode(Endpoint endpoint, boolean mode) {
        portService.updateOnlineMode(portCarrier, endpoint, mode);
    }

    @Override
    public void setPortLinkMode(PortFacts portFacts) {
        portService.updateLinkStatus(portCarrier, portFacts.getEndpoint(), portFacts.getLinkStatus());
    }

    @Override
    public void setupBfdPortHandler(BfdPortFacts portFacts) {
        bfdPortService.setup(bfdPortCarrier, portFacts);
    }

    @Override
    public void removeBfdPortHandler(Endpoint logicalEndpoint) {
        bfdPortService.remove(bfdPortCarrier, logicalEndpoint);
    }

    @Override
    public void setBfdPortLinkMode(PortFacts logicalPortFacts) {
        bfdPortService.updateLinkStatus(bfdPortCarrier, logicalPortFacts);
    }

    @Override
    public void setBfdPortOnlineMode(Endpoint endpoint, boolean mode) {
        bfdPortService.updateOnlineMode(bfdPortCarrier, endpoint, mode);
    }

    @Override
    public void notifyIslUp(Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        islService.islUp(islCarrier, endpoint, reference, islData);
    }

    @Override
    public void notifyIslDown(Endpoint endpoint, IslReference reference, boolean isPhysicalDown) {
        islService.islDown(islCarrier, endpoint, reference, isPhysicalDown);
    }

    @Override
    public void notifyIslMove(Endpoint endpoint, IslReference reference) {
        islService.islMove(islCarrier, endpoint, reference);
    }

    public void triggerReroute(RerouteFlows trigger) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }
}
