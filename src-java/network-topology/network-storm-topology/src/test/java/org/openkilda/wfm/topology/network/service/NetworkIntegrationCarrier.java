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
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.PortProperties;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.model.BfdSessionData;
import org.openkilda.wfm.topology.network.model.BfdStatusUpdate;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.OnlineStatus;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;
import org.openkilda.wfm.topology.network.utils.EndpointStatusMonitor;
import org.openkilda.wfm.topology.network.utils.SwitchOnlineStatusMonitor;

import lombok.Data;

import java.time.Instant;

@Data
public class NetworkIntegrationCarrier
        implements ISwitchCarrier, IPortCarrier, IUniIslCarrier, IIslCarrier,
        IBfdLogicalPortCarrier, IBfdSessionCarrier, IBfdGlobalToggleCarrier {
    private final NetworkSwitchService switchService;

    private final NetworkPortService portService;

    private final NetworkUniIslService uniIslService;

    private final NetworkIslService islService;

    private final NetworkBfdLogicalPortService bfdLogicalPortService;

    private final NetworkBfdSessionService bfdSessionService;

    private final NetworkBfdGlobalToggleService bfdGlobalToggleService;

    private ISwitchCarrier switchCarrier = this;
    private IPortCarrier portCarrier = this;
    private IUniIslCarrier uniIslCarrier = this;
    private IIslCarrier islCarrier = this;
    private IBfdLogicalPortCarrier bfdLogicalPortCarrier = this;
    private IBfdSessionCarrier bfdSessionCarrier = this;
    private IBfdGlobalToggleCarrier bfdGlobalToggleCarrier = this;

    private final SwitchOnlineStatusMonitor switchOnlineStatusMonitor = new SwitchOnlineStatusMonitor();
    private final EndpointStatusMonitor endpointStatusMonitor = new EndpointStatusMonitor();

    public NetworkIntegrationCarrier(NetworkOptions options, PersistenceManager persistenceManager) {
        switchService = new NetworkSwitchService(this, persistenceManager, options);
        portService = new NetworkPortService(this, persistenceManager);
        uniIslService = new NetworkUniIslService(this);
        islService = new NetworkIslService(this, persistenceManager, options);

        bfdLogicalPortService = new NetworkBfdLogicalPortService(
                this, switchOnlineStatusMonitor, options.getBfdLogicalPortOffset());
        bfdSessionService = new NetworkBfdSessionService(
                persistenceManager, switchOnlineStatusMonitor, endpointStatusMonitor, this);
        bfdGlobalToggleService = new NetworkBfdGlobalToggleService(this, persistenceManager);
    }

    @Override
    public void bfdPropertiesApplyRequest(Endpoint physical, IslReference reference, BfdProperties properties) {
        bfdLogicalPortService.apply(physical, reference, properties);
    }

    @Override
    public void bfdDisableRequest(Endpoint physicalEndpoint) {
        bfdSessionService.disable(physicalEndpoint);
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
    public void notifyPortPropertiesChanged(PortProperties portProperties) {
        // Northbound service is not covered by this test
    }

    @Override
    public void notifyPortRoundTripStatus(RoundTripStatus status) {
        uniIslService.roundTripStatusNotification(status);
    }

    @Override
    public String createLogicalPort(Endpoint logical, int physicalPortNumber) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
        return "dummy";
    }

    @Override
    public String deleteLogicalPort(Endpoint logical) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
        return "dummy";
    }

    @Override
    public void enableUpdateSession(Endpoint logical, int physicalPortNumber, BfdSessionData sessionData) {
        bfdSessionService.enableUpdate(logical, physicalPortNumber, sessionData);
    }

    @Override
    public void disableSession(Endpoint logical) {
        bfdSessionService.disable(logical);
    }

    @Override
    public void logicalPortControllerAddNotification(Endpoint physical) {
        bfdGlobalToggleService.create(physical);
    }

    @Override
    public void logicalPortControllerDelNotification(Endpoint physical) {
        bfdGlobalToggleService.delete(physical);
    }

    @Override
    public String sendWorkerBfdSessionCreateRequest(NoviBfdSession bfdSession) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
        return "dummy";
    }

    @Override
    public String sendWorkerBfdSessionDeleteRequest(NoviBfdSession bfdSession) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
        return "dummy";
    }

    @Override
    public void sessionRotateRequest(Endpoint logical, boolean error) {
        bfdSessionService.rotate(logical, error);
    }

    @Override
    public void sessionCompleteNotification(Endpoint physical) {
        bfdLogicalPortService.sessionCompleteNotification(physical);
    }

    @Override
    public void bfdUpNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.UP);
    }

    @Override
    public void bfdDownNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.DOWN);
    }

    @Override
    public void bfdKillNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.KILL);
    }

    @Override
    public void bfdFailNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.FAIL);
    }

    @Override
    public void filteredBfdUpNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.UP);
    }

    @Override
    public void filteredBfdDownNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.DOWN);
    }

    @Override
    public void filteredBfdKillNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.KILL);
    }

    @Override
    public void filteredBfdFailNotification(Endpoint physicalEndpoint) {
        uniIslService.uniIslBfdStatusUpdate(physicalEndpoint, BfdStatusUpdate.FAIL);
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
    public void setOnlineMode(Endpoint endpoint, OnlineStatus onlineStatus) {
        portService.updateOnlineMode(endpoint, onlineStatus);
    }

    @Override
    public void setPortLinkMode(Endpoint endpoint, LinkStatus linkStatus) {
        portService.updateLinkStatus(endpoint, linkStatus);
    }

    @Override
    public void sendBfdPortAdd(Endpoint endpoint, int physicalPortNumber) {
        bfdLogicalPortService.portAdd(endpoint, physicalPortNumber);
    }

    @Override
    public void sendBfdPortDelete(Endpoint logicalEndpoint) {
        bfdLogicalPortService.portDel(logicalEndpoint);
    }

    @Override
    public void sendBfdLinkStatusUpdate(Endpoint logicalEndpoint, LinkStatus linkStatus) {
        endpointStatusMonitor.update(logicalEndpoint, linkStatus);
    }

    @Override
    public void sendSwitchSynchronizeRequest(String key, SwitchId switchId) {
    }

    @Override
    public void sendAffectedFlowRerouteRequest(SwitchId switchId) {
    }

    @Override
    public void sendSwitchStateChanged(SwitchId switchId, SwitchStatus status) {

    }

    @Override
    public void switchRemovedNotification(SwitchId switchId) {
        switchOnlineStatusMonitor.cleanup(switchId);
        endpointStatusMonitor.cleanup(switchId);
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
    public void sendPortStateChangedHistory(Endpoint endpoint, PortHistoryEvent event, Instant time) {
    }

    @Override
    public void notifyIslMove(Endpoint endpoint, IslReference reference) {
        islService.islMove(endpoint, reference);
    }

    @Override
    public void notifyIslRoundTripStatus(IslReference reference, RoundTripStatus status) {
        islService.roundTripStatusNotification(reference, status);
    }

    @Override
    public void notifyBfdStatus(Endpoint endpoint, IslReference reference, BfdStatusUpdate status) {
        islService.bfdStatusUpdate(endpoint, reference, status);
    }

    @Override
    public void exhaustedPollModeUpdateRequest(Endpoint endpoint, boolean enableExhaustedPollMode) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    public void triggerReroute(RerouteFlows trigger) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    @Override
    public void islStatusUpdateNotification(IslStatusUpdateNotification trigger) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    @Override
    public void islDefaultRulesInstall(Endpoint source, Endpoint destination) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    @Override
    public void islDefaultRulesDelete(Endpoint source, Endpoint destination) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    @Override
    public void auxiliaryPollModeUpdateRequest(Endpoint endpoint, boolean enableAuxiliaryPollMode) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }

    @Override
    public void islRemovedNotification(Endpoint srcEndpoint, IslReference reference) {
        // Real implementation emit event into external component, i.e.it is outside scope of this integration test.
    }
}
