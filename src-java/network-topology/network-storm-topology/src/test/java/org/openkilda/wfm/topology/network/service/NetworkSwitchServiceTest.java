/* Copyright 2021 Telstra Open Source
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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.error.rule.SwitchSyncErrorData;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.switches.GroupSyncEntry;
import org.openkilda.messaging.info.switches.LogicalPortsSyncEntry;
import org.openkilda.messaging.info.switches.MetersSyncEntry;
import org.openkilda.messaging.info.switches.RulesSyncEntry;
import org.openkilda.messaging.info.switches.SwitchSyncResponse;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchPortView.State;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Isl;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SpeakerRepository;
import org.openkilda.persistence.repositories.SwitchConnectRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionCallbackWithoutResult;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.OnlineStatus;
import org.openkilda.wfm.topology.network.model.PortDataHolder;
import org.openkilda.wfm.topology.network.model.facts.HistoryFacts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class NetworkSwitchServiceTest {
    private static final int BFD_LOGICAL_PORT_OFFSET = 1000;
    private static final int BFD_LOGICAL_PORT_MAX_NUMBER = 1999;
    private static final int LAG_LOGICAL_PORT_OFFSET = 2000;
    private static final int SYNC_ATTEMPTS = 2;
    private static final long MAX_SPEED = 10000000;
    private static final long CURRENT_SPEED = 999999;

    private static final String DUMMY_CORRELATION_ID = "dummy";

    private NetworkOptions options = NetworkOptions.builder()
            .bfdLogicalPortOffset(BFD_LOGICAL_PORT_OFFSET)
            .bfdLogicalPortMaxNumber(BFD_LOGICAL_PORT_MAX_NUMBER)
            .lagLogicalPortOffset(LAG_LOGICAL_PORT_OFFSET)
            .dbRepeatMaxDurationSeconds(30)
            .countSynchronizationAttempts(SYNC_ATTEMPTS)
            .switchOfflineGenerationLag(2)
            .build();

    @Mock
    private ISwitchCarrier carrier;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private RepositoryFactory repositoryFactory;

    @Mock
    private SwitchRepository switchRepository;

    @Mock
    private SwitchConnectRepository switchConnectRepository;

    @Mock
    private KildaConfigurationRepository kildaConfigurationRepository;

    @Mock
    private SwitchPropertiesRepository switchPropertiesRepository;

    @Mock
    private KildaFeatureTogglesRepository featureTogglesRepository;

    @Mock
    private SpeakerRepository speakerRepository;

    private final SpeakerSwitchDescription switchDescription = SpeakerSwitchDescription.builder()
            .manufacturer("OF vendor A")
            .hardware("AHW-0")
            .software("AOS-1")
            .serialNumber("aabbcc")
            .datapath("OpenFlow switch AABBCC")
            .build();

    private final IpSocketAddress speakerInetAddress = new IpSocketAddress("127.1.0.254", 6653);

    private final SwitchId alphaDatapath = new SwitchId(1);
    private final IpSocketAddress alphaInetAddress = new IpSocketAddress("127.1.0.1", 32768);

    private final SwitchId betaDatapath = new SwitchId(2);
    private final InetSocketAddress betaInetAddress = new InetSocketAddress(
            Inet4Address.getByName("127.1.0.2"), 32768);

    private final String alphaDescription = String.format("%s OF_13 %s",
            switchDescription.getManufacturer(),
            switchDescription.getSoftware());


    public NetworkSwitchServiceTest() throws UnknownHostException {
    }

    @BeforeEach
    public void setup() {
        resetMocks();
    }

    private void resetMocks() {
        reset(carrier);

        reset(persistenceManager);
        lenient().when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        lenient().when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        reset(transactionManager);

        lenient().when(transactionManager.getDefaultRetryPolicy())
                .thenReturn(RetryPolicy.builder().withMaxRetries(2));
        lenient()
                .doAnswer(invocation -> {
                    RetryPolicy<?> retryPolicy = invocation.getArgument(0);
                    TransactionCallbackWithoutResult<?> tr = invocation.getArgument(1);
                    Failsafe.with(retryPolicy).run(tr::doInTransaction);
                    return null;
                }).when(transactionManager)
                .doInTransaction(any(RetryPolicy.class), any(TransactionCallbackWithoutResult.class));

        reset(switchRepository, switchPropertiesRepository);

        lenient().doAnswer(invocation -> invocation.getArgument(0)).when(switchRepository).add(any());

        lenient().when(kildaConfigurationRepository.getOrDefault()).thenReturn(KildaConfiguration.DEFAULTS);
        lenient().when(featureTogglesRepository.getOrDefault()).thenReturn(KildaFeatureToggles.DEFAULTS);

        reset(repositoryFactory);
        lenient().when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        lenient().when(repositoryFactory.createSwitchConnectRepository()).thenReturn(switchConnectRepository);
        lenient().when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);
        lenient().when(repositoryFactory.createKildaConfigurationRepository()).thenReturn(kildaConfigurationRepository);
        lenient().when(repositoryFactory.createSpeakerRepository()).thenReturn(speakerRepository);
        lenient().when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);
    }

    @Test
    public void newSwitch() {
        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verifyNewSwitchAfterSwitchSync(ports);
    }

    @Test
    public void switchFromOnlineToOffline() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();
        PortDataHolder portDataHolder = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        resetMocks();

        when(switchRepository.findById(alphaDatapath)).thenReturn(
                Optional.of(Switch.builder().switchId(alphaDatapath)
                        .build()));

        SwitchInfoData deactivatedSwitch = switchAddEvent.toBuilder().state(SwitchChangeType.DEACTIVATED).build();

        service.switchEvent(deactivatedSwitch);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).sendSwitchStateChanged(eq(alphaDatapath), eq(SwitchStatus.INACTIVE));
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), OnlineStatus.OFFLINE,
                portDataHolder);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), OnlineStatus.OFFLINE,
                portDataHolder);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void switchFromHistoryToOffline() {

        when(switchRepository.findById(alphaDatapath)).thenReturn(
                Optional.of(Switch.builder().switchId(alphaDatapath)
                        .build()));

        HistoryFacts history = new HistoryFacts(alphaDatapath, SwitchStatus.INACTIVE);
        PortDataHolder portData = new PortDataHolder(0, 0);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islAtoB = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islAtoB2 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(2)
                .destSwitch(betaSwitch)
                .destPort(2).build();

        history.addLink(islAtoB);
        history.addLink(islAtoB2);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchAddWithHistory(history);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 1), islAtoB);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 2), islAtoB2);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 1), OnlineStatus.OFFLINE,
                portData);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 2), OnlineStatus.OFFLINE,
                portData);
    }

    @Test
    public void switchFromHistoryToOfflineToOnlineRemovedPort() {

        // History

        HistoryFacts history = new HistoryFacts(alphaDatapath, SwitchStatus.ACTIVE);

        Switch alphaSwitch = Switch.builder().switchId(alphaDatapath).build();
        Switch betaSwitch = Switch.builder().switchId(betaDatapath).build();

        Isl islAtoB = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(1)
                .destSwitch(betaSwitch)
                .destPort(1).build();

        Isl islAtoB2 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(2)
                .destSwitch(betaSwitch)
                .destPort(2).build();

        Isl islAtoB3 = Isl.builder()
                .srcSwitch(alphaSwitch)
                .srcPort(3)
                .destSwitch(betaSwitch)
                .destPort(3).build();

        history.addLink(islAtoB);
        history.addLink(islAtoB2);
        history.addLink(islAtoB3);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchAddWithHistory(history);

        // Online

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        resetMocks();

        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 3));
        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());
    }

    @Test
    public void switchFromOnlineToOfflineToOnline() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        SwitchInfoData deactivatedSwitch = switchAddEvent.toBuilder().state(SwitchChangeType.DEACTIVATED).build();

        service.switchEvent(deactivatedSwitch);

        List<SpeakerSwitchPortView> ports2 = getSpeakerSwitchPortViewsRevert();

        SpeakerSwitchView speakerSwitchView2 = getSpeakerSwitchView().toBuilder()
                .ports(ports2)
                .build();

        SwitchInfoData switchAddEvent2 = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView2);

        resetMocks();

        service.switchEvent(switchAddEvent2);
        verifySwitchSync(service);

        // System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).sendSwitchStateChanged(eq(alphaDatapath), eq(SwitchStatus.ACTIVE));
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), OnlineStatus.ONLINE,
                portData);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), OnlineStatus.ONLINE,
                portData);

        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports2.get(2).getNumber()),
                LinkStatus.of(ports2.get(2).getState()), portData);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, ports2.get(3).getNumber()),
                LinkStatus.of(ports2.get(3).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports2.get(0).getNumber()),
                LinkStatus.of(ports2.get(0).getState()), portData);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, ports2.get(1).getNumber()),
                LinkStatus.of(ports2.get(0).getState()));
        verify(carrier).sendAffectedFlowRerouteRequest(alphaDatapath);
    }

    @Test
    public void switchFromOnlineToOnline() {
        List<SpeakerSwitchPortView> ports = Lists.newArrayList(
                new SpeakerSwitchPortView(1, SpeakerSwitchPortView.State.UP, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(2, SpeakerSwitchPortView.State.DOWN, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(2 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN),

                new SpeakerSwitchPortView(3, SpeakerSwitchPortView.State.UP, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(3 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(4, SpeakerSwitchPortView.State.DOWN, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(4 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN));
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ImmutableList.copyOf(ports))
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        // initial switch ADD
        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        resetMocks();

        // periodic network sync (swap UP/DOWN state for half of the ports)
        for (int idx = 0; idx < 4 && idx < ports.size(); idx++) {
            ports.set(idx, makePortEntryWithOppositeState(ports.get(idx)));
        }

        SpeakerSwitchView periodicSyncEvent = speakerSwitchView.toBuilder().ports(ImmutableList.copyOf(ports)).build();
        service.switchBecomeManaged(periodicSyncEvent, DUMMY_CORRELATION_ID);

        // only changed ports
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 1), LinkStatus.DOWN, portData);
        verify(carrier).sendBfdLinkStatusUpdate(
                Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.DOWN);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 2), LinkStatus.UP, portData);
        verify(carrier).sendBfdLinkStatusUpdate(
                Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.UP);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void switchFromOnlineToOnlineWithLostBfdFeature() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        PortDataHolder portData = new PortDataHolder(0, 0);

        List<SpeakerSwitchPortView> ports = doSpeakerOnline(service, Collections.singleton(SwitchFeature.BFD));
        List<SpeakerSwitchPortView> ports2 = swapBfdPortsState(ports);

        resetMocks();

        service.switchBecomeManaged(getSpeakerSwitchView().toBuilder()
                .features(Collections.emptySet())
                .ports(ports2)
                .build(), DUMMY_CORRELATION_ID);

        // System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).sendBfdPortDelete(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), null);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), OnlineStatus.ONLINE,
                portData);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.DOWN,
                portData);

        verify(carrier).sendBfdPortDelete(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), null);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), OnlineStatus.ONLINE,
                portData);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.UP,
                portData);
    }

    @Test
    public void switchFromOnlineToOnlineWithAcquireBfdFeature() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        List<SpeakerSwitchPortView> ports = doSpeakerOnline(service, Collections.emptySet());
        List<SpeakerSwitchPortView> ports2 = swapBfdPortsState(ports);

        resetMocks();

        service.switchBecomeManaged(getSpeakerSwitchView().toBuilder()
                .features(Collections.singleton(SwitchFeature.BFD))
                .ports(ports2)
                .build(), DUMMY_CORRELATION_ID);

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).sendBfdPortAdd(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), 1);
        verify(carrier).sendBfdLinkStatusUpdate(
                Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.DOWN);

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).sendBfdPortAdd(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), 2);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.UP);
    }

    @Test
    public void portAddEventOnOnlineSwitch() {
        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                getSpeakerSwitchView());
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, MAX_SPEED, CURRENT_SPEED,
                PortChangeType.ADD));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET,
                PortChangeType.ADD));

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 1), null);
        verify(carrier).sendBfdPortAdd(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), 1);

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 1), OnlineStatus.ONLINE, portData);
    }

    @Test
    public void portUpDownEventsOnOnlineSwitch() {

        List<SpeakerSwitchPortView> portsDown = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.DOWN, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.DOWN));
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(portsDown)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);
        resetMocks();
        //System.out.println(mockingDetails(carrier).printInvocations());

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, MAX_SPEED,
                CURRENT_SPEED, PortChangeType.UP));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET,
                PortChangeType.UP));
        List<SpeakerSwitchPortView> portsUp = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.UP, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.UP));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, portsUp.get(0).getNumber()),
                LinkStatus.of(portsUp.get(0).getState()), portData);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, portsUp.get(1).getNumber()),
                LinkStatus.of(portsUp.get(1).getState()));

        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, MAX_SPEED, CURRENT_SPEED,
                PortChangeType.DOWN));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET,
                PortChangeType.DOWN));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, portsDown.get(0).getNumber()),
                LinkStatus.of(portsDown.get(0).getState()), portData);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, portsDown.get(1).getNumber()),
                LinkStatus.of(portsDown.get(1).getState()));

        // System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void portUpdateEventOnOnlineSwitch() {
        List<SpeakerSwitchPortView> ports = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.UP, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.UP));
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchUpdateEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchUpdateEvent);
        verifySwitchSync(service);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, ports.get(0).getNumber(), MAX_SPEED,
                CURRENT_SPEED, PortChangeType.OTHER_UPDATE));
        service.switchPortEvent(new PortInfoData(alphaDatapath, ports.get(1).getNumber(), PortChangeType.OTHER_UPDATE));

        verify(carrier).updatePortHandler(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), portData);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void portAddOnOnlineSwitch() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        // prepare
        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(emptyList())
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        // process
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);
        PortInfoData speakerPortEvent = new PortInfoData(endpoint.getDatapath(), endpoint.getPortNumber(),
                MAX_SPEED, CURRENT_SPEED, PortChangeType.ADD, true);
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        service.switchPortEvent(speakerPortEvent);
        verify(carrier).sendSwitchStateChanged(alphaDatapath, SwitchStatus.ACTIVE);
        verify(carrier).setupPortHandler(endpoint, null);
        verify(carrier).setOnlineMode(endpoint, OnlineStatus.ONLINE, portData);
        verify(carrier).setPortLinkMode(endpoint, LinkStatus.UP, portData);
        verify(carrier).sendAffectedFlowRerouteRequest(alphaDatapath);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void portDelEventOnOnlineSwitch() {

        List<SpeakerSwitchPortView> portsDown = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.DOWN, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.DOWN));

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(portsDown)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.DELETE));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET,
                PortChangeType.DELETE));

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 1));
        verify(carrier).sendBfdPortDelete(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void switchWithNoBfdSupport() {
        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);
        PortDataHolder bfdPortData = new PortDataHolder(0, 0);

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .features(Collections.emptySet())
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        // System.out.println(mockingDetails(carrier).printInvocations());
        // System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), null);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), null);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), null);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), null);

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), OnlineStatus.ONLINE,
                portData);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), OnlineStatus.ONLINE,
                bfdPortData);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), OnlineStatus.ONLINE,
                portData);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), OnlineStatus.ONLINE,
                bfdPortData);

        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()),
                LinkStatus.of(ports.get(2).getState()), portData);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()),
                LinkStatus.of(ports.get(3).getState()), bfdPortData);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()),
                LinkStatus.of(ports.get(0).getState()), portData);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()),
                LinkStatus.of(ports.get(1).getState()), bfdPortData);
    }

    @Test
    public void newSwitchWithWrongSynchronizationResponse() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);

        RulesSyncEntry rulesSyncEntry =
                new RulesSyncEntry(
                        singletonList(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1).getValue()),
                        emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        MetersSyncEntry metersSyncEntry =
                new MetersSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        GroupSyncEntry groupSyncEntry = new GroupSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList());
        SwitchSyncResponse response = new SwitchSyncResponse(alphaDatapath, rulesSyncEntry, metersSyncEntry,
                groupSyncEntry, LogicalPortsSyncEntry.builder().build());

        // for a randomly generated key in SwitchFsm
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(carrier).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        service.switchManagerResponse(response, captor.getValue());

        verify(carrier, times(SYNC_ATTEMPTS)).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        service.switchManagerResponse(response, captor.getValue());

        verifyNewSwitchAfterSwitchSync(ports);
        verify(carrier).sendSwitchStateChanged(alphaDatapath, SwitchStatus.ACTIVE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void newSwitchWithSynchronizationErrorResponse() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);

        // for a randomly generated key in SwitchFsm
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(carrier).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        SwitchSyncErrorData errorData = new SwitchSyncErrorData(alphaDatapath, null, null, null);
        service.switchManagerErrorResponse(errorData, captor.getValue());

        verify(carrier, times(SYNC_ATTEMPTS)).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        service.switchManagerErrorResponse(errorData, captor.getValue());

        verifyNewSwitchAfterSwitchSync(ports);
        verify(carrier).sendSwitchStateChanged(alphaDatapath, SwitchStatus.ACTIVE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void newSwitchWithSynchronizationTimeout() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);

        // for a randomly generated key in SwitchFsm
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(carrier).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        service.switchManagerTimeout(alphaDatapath, captor.getValue());

        verify(carrier, times(SYNC_ATTEMPTS)).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        service.switchManagerTimeout(alphaDatapath, captor.getValue());

        verifyNewSwitchAfterSwitchSync(ports);
        verify(carrier).sendSwitchStateChanged(alphaDatapath, SwitchStatus.ACTIVE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void newSwitchWithSynchronizationSuccessAfterError() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);

        // for a randomly generated key in SwitchFsm
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        verify(carrier).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        SwitchSyncErrorData errorData = new SwitchSyncErrorData(alphaDatapath, null, null, null);
        service.switchManagerErrorResponse(errorData, captor.getValue());

        RulesSyncEntry rulesSyncEntry =
                new RulesSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        MetersSyncEntry metersSyncEntry =
                new MetersSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        GroupSyncEntry groupSyncEntry = new GroupSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList());
        SwitchSyncResponse response = new SwitchSyncResponse(alphaDatapath, rulesSyncEntry, metersSyncEntry,
                groupSyncEntry, LogicalPortsSyncEntry.builder().build());


        verify(carrier, times(SYNC_ATTEMPTS)).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));
        service.switchManagerResponse(response, captor.getValue());

        verifyNewSwitchAfterSwitchSync(ports);
        verify(carrier).sendSwitchStateChanged(alphaDatapath, SwitchStatus.ACTIVE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void newSwitchWithNullMetersInSynchronizationResponse() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);
        service.switchEvent(switchAddEvent);

        // for a randomly generated key in SwitchFsm
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(carrier).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));

        RulesSyncEntry rulesSyncEntry =
                new RulesSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        GroupSyncEntry groupSyncEntry = new GroupSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList());
        SwitchSyncResponse response = new SwitchSyncResponse(alphaDatapath, rulesSyncEntry, null, groupSyncEntry,
                LogicalPortsSyncEntry.builder().build());
        service.switchManagerResponse(response, captor.getValue());

        verifyNewSwitchAfterSwitchSync(ports);
        verify(carrier).sendSwitchStateChanged(alphaDatapath, SwitchStatus.ACTIVE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void switchShouldBecomeOfflineEvenIfOfflineEventHaveBeenLost() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        SpeakerSwitchView alpha = getSpeakerSwitchView();

        service.switchAddWithHistory(new HistoryFacts(betaDatapath, SwitchStatus.ACTIVE));
        verifyNoInteractions(carrier);

        service.switchBecomeManaged(alpha, "A-0");
        verify(carrier).sendSwitchSynchronizeRequest(any(), eq(alpha.getDatapath()));
        verifyNoMoreInteractions(carrier);

        service.switchBecomeManaged(alpha, "A-1");
        verify(carrier).sendSwitchStateChanged(betaDatapath, SwitchStatus.INACTIVE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void switchMustStayOnlineIfItReceivePeriodicDumps() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager, options);

        SpeakerSwitchView dump = getSpeakerSwitchView();
        service.switchBecomeManaged(dump, "A-0");

        ArgumentCaptor<String> syncKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(carrier).sendSwitchSynchronizeRequest(syncKeyCaptor.capture(), eq(dump.getDatapath()));
        verifyNoMoreInteractions(carrier);

        String syncKey = syncKeyCaptor.getValue();
        SwitchSyncResponse response = makeSuccessSwitchSyncResponse(dump.getDatapath());
        service.switchManagerResponse(response, syncKey);
        verify(carrier).sendSwitchStateChanged(dump.getDatapath(), SwitchStatus.ACTIVE);
        verify(carrier).sendAffectedFlowRerouteRequest(dump.getDatapath());
        verifyNoMoreInteractions(carrier);

        // +10 to be sure to cover +-1 mismatches
        for (int i = 0; i < options.getSwitchOfflineGenerationLag() + 10; i++) {
            service.switchBecomeManaged(dump, String.format("dump#%d", i));
            verifyNoMoreInteractions(carrier);
        }
    }

    private void verifySwitchSync(NetworkSwitchService service) {
        // for a randomly generated key in SwitchFsm
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(carrier).sendSwitchSynchronizeRequest(captor.capture(), eq(alphaDatapath));

        SwitchSyncResponse response = makeSuccessSwitchSyncResponse(alphaDatapath);
        service.switchManagerResponse(response, captor.getValue());
    }

    private void verifyNewSwitchAfterSwitchSync(List<SpeakerSwitchPortView> ports) {
        PortDataHolder portData = new PortDataHolder(MAX_SPEED, CURRENT_SPEED);

        verify(carrier).sendSwitchStateChanged(eq(alphaDatapath), eq(SwitchStatus.ACTIVE));
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), null);
        verify(carrier).sendBfdPortAdd(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), 1);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), null);
        verify(carrier).sendBfdPortAdd(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), 2);

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), OnlineStatus.ONLINE,
                portData);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), OnlineStatus.ONLINE,
                portData);

        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()),
                LinkStatus.of(ports.get(2).getState()), portData);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, ports.get(3).getNumber()),
                LinkStatus.of(ports.get(3).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()),
                LinkStatus.of(ports.get(0).getState()), portData);
        verify(carrier).sendBfdLinkStatusUpdate(Endpoint.of(alphaDatapath, ports.get(1).getNumber()),
                LinkStatus.of(ports.get(0).getState()));
        verify(carrier).sendAffectedFlowRerouteRequest(alphaDatapath);

        verify(switchPropertiesRepository).add(argThat(s ->
                s.getSupportedTransitEncapsulation().equals(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)));
    }

    private List<SpeakerSwitchPortView> doSpeakerOnline(NetworkSwitchService service, Set<SwitchFeature> features) {
        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();
        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .features(features)
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        service.switchEvent(switchAddEvent);
        verifySwitchSync(service);

        return ports;
    }

    private SpeakerSwitchView getSpeakerSwitchView() {
        return SpeakerSwitchView.builder()
                .datapath(alphaDatapath)
                .switchSocketAddress(alphaInetAddress)
                .speakerSocketAddress(speakerInetAddress)
                .ofVersion("OF_13")
                .description(switchDescription)
                .features(Collections.singleton(SwitchFeature.BFD))
                .build();
    }

    private List<SpeakerSwitchPortView> getSpeakerSwitchPortViews() {
        return ImmutableList.of(
                new SpeakerSwitchPortView(1, SpeakerSwitchPortView.State.UP, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(2, SpeakerSwitchPortView.State.DOWN, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(2 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN));
    }

    private List<SpeakerSwitchPortView> getSpeakerSwitchPortViewsRevert() {
        return ImmutableList.of(
                new SpeakerSwitchPortView(1, State.DOWN, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.DOWN),
                new SpeakerSwitchPortView(2, State.UP, MAX_SPEED, CURRENT_SPEED),
                new SpeakerSwitchPortView(2 + BFD_LOGICAL_PORT_OFFSET, State.UP));
    }

    private List<SpeakerSwitchPortView> swapBfdPortsState(List<SpeakerSwitchPortView> ports) {
        List<SpeakerSwitchPortView> result = new ArrayList<>();
        for (SpeakerSwitchPortView entry : ports) {
            SpeakerSwitchPortView replace = entry;
            if (BFD_LOGICAL_PORT_OFFSET < entry.getNumber()) {
                replace = makePortEntryWithOppositeState(entry);
            }
            result.add(replace);
        }
        return result;
    }

    private SpeakerSwitchPortView makePortEntryWithOppositeState(SpeakerSwitchPortView port) {
        return SpeakerSwitchPortView.builder()
                .number(port.getNumber())
                .state(port.getState() == State.UP ? State.DOWN : State.UP)
                .build();
    }

    private SwitchSyncResponse makeSuccessSwitchSyncResponse(SwitchId switchId) {
        RulesSyncEntry rulesSyncEntry =
                new RulesSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        MetersSyncEntry metersSyncEntry =
                new MetersSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        GroupSyncEntry groupSyncEntry = new GroupSyncEntry(emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList());
        return new SwitchSyncResponse(switchId, rulesSyncEntry, metersSyncEntry, groupSyncEntry,
                LogicalPortsSyncEntry.builder().build());
    }
}
