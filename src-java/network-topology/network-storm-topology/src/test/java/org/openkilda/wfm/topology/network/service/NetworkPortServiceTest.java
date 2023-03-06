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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Port;
import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.PortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionCallbackWithoutResult;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.OnlineStatus;
import org.openkilda.wfm.topology.network.model.PortDataHolder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class NetworkPortServiceTest {
    private static final long MAX_SPEED = 10000000;
    private static final long CURRENT_SPEED = 99999;

    @Mock
    private IPortCarrier carrier;

    @Mock
    private NetworkTopologyDashboardLogger dashboardLogger;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private RepositoryFactory repositoryFactory;

    @Mock
    private PortPropertiesRepository portPropertiesRepository;

    @Mock
    private PortRepository portRepository;

    @Mock
    private SwitchRepository switchRepository;

    private final SwitchId alphaDatapath = new SwitchId(1);

    @Before
    public void setup() {
        resetMocks();
    }

    private void resetMocks() {
        reset(carrier);
        reset(dashboardLogger);

        reset(persistenceManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        doAnswer(invocation -> {
            TransactionCallbackWithoutResult tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(any(TransactionCallbackWithoutResult.class));

        reset(portPropertiesRepository);
        reset(portRepository);
        doAnswer(invocation -> invocation.getArgument(0))
                .when(portPropertiesRepository).add(any());

        reset(switchRepository);
        reset(repositoryFactory);

        when(repositoryFactory.createPortPropertiesRepository()).thenReturn(portPropertiesRepository);
        when(repositoryFactory.createPortRepository()).thenReturn(portRepository);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
    }

    @Test
    public void createPort() {
        NetworkPortService service = makeService();
        Endpoint port1 = Endpoint.of(alphaDatapath, 1);
        Endpoint port2 = Endpoint.of(alphaDatapath, 2);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port1.getPortNumber()))
                .thenReturn(Optional.empty());
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port2.getPortNumber()))
                .thenReturn(Optional.empty());

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.ONLINE, portData);
        service.setup(port2, null);
        service.updateOnlineMode(port2, OnlineStatus.OFFLINE, portData);

        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, port1.getPortNumber()), null);
        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, port2.getPortNumber()), null);

        verify(portRepository).add(Port.builder()
                .switchObj(getDefaultSwitch())
                .maxSpeed(MAX_SPEED)
                .currentSpeed(CURRENT_SPEED)
                .portNo(port1.getPortNumber())
                .switchId(getDefaultSwitch().getSwitchId())
                .build());
        verify(portRepository, never()).getBySwitchIdAndPort(alphaDatapath, port2.getPortNumber());

        resetMocks();

        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port1.getPortNumber()))
                .thenReturn(Optional.of(buildPort(port1.getPortNumber())));
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port2.getPortNumber()))
                .thenReturn(Optional.of(buildPort(port2.getPortNumber())));
        doNothing().when(portRepository).remove(buildPort(port1.getPortNumber()));
        doNothing().when(portRepository).remove(buildPort(port2.getPortNumber()));

        service.remove(port1);
        service.remove(port2);

        verify(carrier).removeUniIslHandler(Endpoint.of(alphaDatapath, port1.getPortNumber()));
        verify(carrier).removeUniIslHandler(Endpoint.of(alphaDatapath, port2.getPortNumber()));
        verify(portRepository).remove(buildPort(port1.getPortNumber()));
        verify(portRepository).remove(buildPort(port2.getPortNumber()));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void updatePort() {
        NetworkPortService service = makeService();
        Endpoint port1 = Endpoint.of(alphaDatapath, 1);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        //update on online->port_up->port_data events

        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(port1, LinkStatus.UP, portData);
        service.update(port1, portData);

        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, port1.getPortNumber()), null);
        verify(portRepository, times(3)).getBySwitchIdAndPort(getDefaultSwitch().getSwitchId(),
                port1.getPortNumber());
        resetMocks();

        //update on online->port_down->port_data events

        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(port1, LinkStatus.DOWN, portData);
        service.update(port1, portData);

        verify(portRepository, times(2)).getBySwitchIdAndPort(getDefaultSwitch().getSwitchId(),
                port1.getPortNumber());
        resetMocks();

        //update on online->port_up->port_up_disabled->port_data events

        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(port1, LinkStatus.UP, portData);
        service.updatePortProperties(port1, false);
        service.update(port1, portData);

        verify(portRepository, times(3)).getBySwitchIdAndPort(getDefaultSwitch().getSwitchId(),
                port1.getPortNumber());

        // System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void inOperationalUpDownPort() {
        NetworkPortService service = makeService();
        Endpoint port1 = Endpoint.of(alphaDatapath, 1);
        Endpoint port2 = Endpoint.of(alphaDatapath, 2);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port1.getPortNumber()))
                .thenReturn(Optional.of(buildPort(port1.getPortNumber())));
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port2.getPortNumber()))
                .thenReturn(Optional.of(buildPort(port2.getPortNumber())));

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.ONLINE, portData);
        service.setup(port2, null);
        service.updateOnlineMode(port2, OnlineStatus.ONLINE, portData);

        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, 2), null);
        verifyNoInteractions(dashboardLogger);

        resetMocks();

        // Port 1 from Unknown to UP then DOWN

        service.updateLinkStatus(port1, LinkStatus.UP, portData);
        service.updateLinkStatus(port1, LinkStatus.DOWN, portData);

        verify(dashboardLogger).onPortUp(port1);
        verify(dashboardLogger).onPortDown(port1);
        verifyNoMoreInteractions(dashboardLogger);

        verify(carrier).enableDiscoveryPoll(Endpoint.of(alphaDatapath, 1));
        verify(carrier).disableDiscoveryPoll(Endpoint.of(alphaDatapath, 1));
        verify(carrier).notifyPortPhysicalDown(Endpoint.of(alphaDatapath, 1));

        resetMocks();

        // Port 2 from Unknown to DOWN then UP

        service.updateLinkStatus(port2, LinkStatus.DOWN, portData);
        service.updateLinkStatus(port2, LinkStatus.UP, portData);

        verify(dashboardLogger).onPortDown(port2);
        verify(dashboardLogger).onPortUp(port2);
        verifyNoMoreInteractions(dashboardLogger);

        verify(carrier).notifyPortPhysicalDown(Endpoint.of(alphaDatapath, 2));
        verify(carrier).enableDiscoveryPoll(Endpoint.of(alphaDatapath, 2));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void inUnOperationalUpDownPort() {
        NetworkPortService service = makeService();
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.of(buildPort(endpoint.getPortNumber())));

        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);

        resetMocks();

        service.updateOnlineMode(endpoint, OnlineStatus.OFFLINE, portData);

        // Port 1 from Unknown to UP then DOWN

        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);
        service.updateLinkStatus(endpoint, LinkStatus.DOWN, portData);

        verifyNoInteractions(dashboardLogger);

        verify(carrier, never()).enableDiscoveryPoll(endpoint);
        verify(carrier, never()).disableDiscoveryPoll(endpoint);
        verify(carrier, never()).notifyPortPhysicalDown(endpoint);

        resetMocks();

        when(portRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.of(buildPort(endpoint.getPortNumber())));

        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);

        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);
        service.updateLinkStatus(endpoint, LinkStatus.DOWN, portData);

        verify(dashboardLogger).onPortUp(endpoint);
        verify(dashboardLogger).onPortDown(endpoint);
        verifyNoMoreInteractions(dashboardLogger);

        verify(carrier).enableDiscoveryPoll(endpoint);
        verify(carrier).disableDiscoveryPoll(endpoint);
        verify(carrier).notifyPortPhysicalDown(endpoint);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_DOWN), any(Instant.class));

        // System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void createPortProperties() {
        NetworkPortService service = makeService();
        int port = 7;
        Endpoint endpoint = Endpoint.of(alphaDatapath, port);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, port))
                .thenReturn(Optional.empty());
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port))
                .thenReturn(Optional.of(buildPort(port)));

        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);
        service.updatePortProperties(endpoint, false);

        service.remove(endpoint);

        verify(carrier).setupUniIslHandler(endpoint, null);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier).enableDiscoveryPoll(endpoint);
        verify(carrier, new Times(2)).disableDiscoveryPoll(endpoint);
        verify(carrier).notifyPortDiscoveryFailed(endpoint);
        verify(carrier).notifyPortPropertiesChanged(any(PortProperties.class));
        verify(carrier).removeUniIslHandler(endpoint);

        verify(portPropertiesRepository).add(PortProperties.builder()
                .switchObj(getDefaultSwitch())
                .port(port)
                .discoveryEnabled(false)
                .build());
    }

    @Test
    public void disableDiscoveryWhenPortDown() {
        NetworkPortService service = makeService();
        int port = 7;
        Endpoint endpoint = Endpoint.of(alphaDatapath, port);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portRepository.getBySwitchIdAndPort(alphaDatapath, port))
                .thenReturn(Optional.of(buildPort(port)));

        expectSwitchLookup(endpoint, getDefaultSwitch());

        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.DOWN, portData);
        service.updatePortProperties(endpoint, false);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);

        service.remove(endpoint);

        verify(carrier).setupUniIslHandler(endpoint, null);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_DOWN), any(Instant.class));
        verify(carrier, new Times(2)).disableDiscoveryPoll(endpoint);
        verify(carrier).notifyPortPhysicalDown(endpoint);
        verify(carrier).notifyPortPropertiesChanged(any(PortProperties.class));
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier, new Times(0)).enableDiscoveryPoll(endpoint);
        verify(carrier).removeUniIslHandler(endpoint);

        verify(portPropertiesRepository).add(PortProperties.builder()
                .switchObj(getDefaultSwitch())
                .port(port)
                .discoveryEnabled(false)
                .build());
    }

    @Test
    public void testDiscoveryEventWhenDiscoveryDisabled() {
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.empty());
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.of(buildPort(endpoint.getPortNumber())));

        NetworkPortService service = makeService();
        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);

        verify(carrier).enableDiscoveryPoll(eq(endpoint));
        verify(carrier, never()).notifyPortDiscovered(eq(endpoint), any(IslInfoData.class));

        service.updatePortProperties(endpoint, false);
        verify(carrier).disableDiscoveryPoll(eq(endpoint));

        Endpoint remote = Endpoint.of(new SwitchId(endpoint.getDatapath().getId() + 1), 1);
        IslInfoData discovery = new IslInfoData(
                new PathNode(endpoint.getDatapath(), endpoint.getPortNumber(), 0),
                new PathNode(remote.getDatapath(), remote.getPortNumber(), 0),
                IslChangeType.DISCOVERED, false);
        service.discovery(endpoint, discovery);
        verify(carrier, never()).notifyPortDiscovered(eq(endpoint), any(IslInfoData.class));
    }

    @Test
    public void testEnableDiscoveryAfterOfflineOnlineCycle() {
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(PortProperties.builder()
                        .switchObj(getDefaultSwitch())
                        .port(endpoint.getPortNumber())
                        .discoveryEnabled(false)
                        .build()));
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));
        when(portRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.of(buildPort(endpoint.getPortNumber())));

        NetworkPortService service = makeService();
        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);
        verify(carrier).enableDiscoveryPoll(eq(endpoint));

        service.updatePortProperties(endpoint, false);
        verify(carrier).disableDiscoveryPoll(eq(endpoint));

        service.updateOnlineMode(endpoint, OnlineStatus.OFFLINE, portData);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);
        // discovery still disabled
        verify(carrier, times(1)).enableDiscoveryPoll(eq(endpoint));

        service.updatePortProperties(endpoint, true);
        verify(carrier, times(2)).enableDiscoveryPoll(eq(endpoint));

        Endpoint remote = Endpoint.of(new SwitchId(endpoint.getDatapath().getId() + 1), 1);
        IslInfoData discovery = new IslInfoData(
                new PathNode(endpoint.getDatapath(), endpoint.getPortNumber(), 0),
                new PathNode(remote.getDatapath(), remote.getPortNumber(), 0),
                IslChangeType.DISCOVERED, false);
        service.discovery(endpoint, discovery);
        verify(carrier).notifyPortDiscovered(eq(endpoint), eq(discovery));
    }

    @Test
    public void ignorePollFailWhenRegionOffline() {
        Endpoint endpoint = Endpoint.of(alphaDatapath, 8);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        when(portRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.of(buildPort(endpoint.getPortNumber())));

        expectSwitchLookup(endpoint, getDefaultSwitch());

        NetworkPortService service = makeService();
        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);

        verify(carrier).setupUniIslHandler(endpoint, null);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier).enableDiscoveryPoll(eq(endpoint));
        verifyNoMoreInteractions(carrier);

        service.updateOnlineMode(endpoint, OnlineStatus.REGION_OFFLINE, portData);
        verify(carrier).disableDiscoveryPoll(endpoint);
        verifyNoMoreInteractions(carrier);

        service.fail(endpoint);
        verifyNoMoreInteractions(carrier);

        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        service.updateLinkStatus(endpoint, LinkStatus.UP, portData);
        verify(carrier).enableDiscoveryPoll(endpoint);
        verifyNoMoreInteractions(carrier);

        service.fail(endpoint);
        verify(carrier).notifyPortDiscoveryFailed(endpoint);
    }

    private NetworkPortService makeService() {
        NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder = mock(
                NetworkTopologyDashboardLogger.Builder.class);
        when(dashboardLoggerBuilder.build(any(Logger.class))).thenReturn(dashboardLogger);

        return new NetworkPortService(carrier, persistenceManager, dashboardLoggerBuilder);
    }

    private void expectSwitchLookup(Endpoint endpoint, Switch entry) {
        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(PortProperties.builder()
                        .switchObj(entry)
                        .port(endpoint.getPortNumber())
                        .discoveryEnabled(false)
                        .build()));
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(entry));
    }

    private Switch getDefaultSwitch() {
        return Switch.builder().switchId(alphaDatapath).build();
    }

    private Port buildPort(int port) {
        return Port.builder()
                .portNo(port)
                .switchId(alphaDatapath)
                .switchObj(getDefaultSwitch())
                .maxSpeed(MAX_SPEED)
                .currentSpeed(CURRENT_SPEED)
                .build();
    }
}
