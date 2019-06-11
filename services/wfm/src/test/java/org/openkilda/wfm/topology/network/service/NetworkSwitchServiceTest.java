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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchPortView.State;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeatures;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchFeaturesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.facts.HistoryFacts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class NetworkSwitchServiceTest {

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
    private SwitchFeaturesRepository switchFeaturesRepository;

    private static final int BFD_LOGICAL_PORT_OFFSET = 200;

    private final SpeakerSwitchDescription switchDescription = SpeakerSwitchDescription.builder()
            .manufacturer("OF vendor A")
            .hardware("AHW-0")
            .software("AOS-1")
            .serialNumber("aabbcc")
            .datapath("OpenFlow switch AABBCC")
            .build();

    private final InetSocketAddress speakerInetAddress = new InetSocketAddress(
            Inet4Address.getByName("127.1.0.254"), 6653);

    private final SwitchId alphaDatapath = new SwitchId(1);
    private final InetSocketAddress alphaInetAddress = new InetSocketAddress(
            Inet4Address.getByName("127.1.0.1"), 32768);

    private final SwitchId betaDatapath = new SwitchId(2);
    private final InetSocketAddress betaInetAddress = new InetSocketAddress(
            Inet4Address.getByName("127.1.0.2"), 32768);

    private final String alphaDescription = String.format("%s OF_13 %s",
            switchDescription.getManufacturer(),
            switchDescription.getSoftware());


    public NetworkSwitchServiceTest() throws UnknownHostException {
    }


    @Before
    public void setup() {
        resetMocks();
    }

    private void resetMocks() {
        reset(carrier);

        reset(persistenceManager);
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        reset(transactionManager);
        doAnswer((Answer) invocation -> {
            TransactionCallbackWithoutResult tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));

        reset(switchRepository, switchFeaturesRepository);

        reset(repositoryFactory);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createSwitchFeaturesRepository()).thenReturn(switchFeaturesRepository);
    }

    @Test
    public void newSwitch() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), null);
        verify(carrier).setupBfdPortHandler(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), 1);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), null);
        verify(carrier).setupBfdPortHandler(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), 2);

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), true);

        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()),
                                        LinkStatus.of(ports.get(2).getState()));
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()),
                                           LinkStatus.of(ports.get(3).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()),
                                        LinkStatus.of(ports.get(0).getState()));
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()),
                                           LinkStatus.of(ports.get(0).getState()));

        verify(switchRepository).createOrUpdate(argThat(sw ->
                sw.getStatus() == SwitchStatus.ACTIVE && sw.getSwitchId() == alphaDatapath));
        verify(switchFeaturesRepository).createOrUpdate(argThat(sf ->
                sf.getSupportedTransitEncapsulation().equals(SwitchFeatures.DEFAULT_FLOW_ENCAPSULATION_TYPES)));
    }

    @Test
    public void switchFromOnlineToOffline() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);

        service.switchEvent(switchAddEvent);

        resetMocks();

        when(switchRepository.findById(alphaDatapath)).thenReturn(
                Optional.of(Switch.builder().switchId(alphaDatapath)
                .build()));

        SwitchInfoData deactivatedSwitch = switchAddEvent.toBuilder().state(SwitchChangeType.DEACTIVATED).build();

        service.switchEvent(deactivatedSwitch);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), false);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), false);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), false);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), false);

        verify(switchRepository).createOrUpdate(argThat(sw ->
                sw.getStatus() == SwitchStatus.INACTIVE && sw.getSwitchId() == alphaDatapath));
    }

    @Test
    public void switchFromHistoryToOffline() {

        when(switchRepository.findById(alphaDatapath)).thenReturn(
                Optional.of(Switch.builder().switchId(alphaDatapath)
                .build()));

        HistoryFacts history = new HistoryFacts(alphaDatapath);

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

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchAddWithHistory(history);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 1), islAtoB);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 2), islAtoB2);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 1), false);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 2), false);

        verify(switchRepository).createOrUpdate(argThat(sw ->
                sw.getStatus() == SwitchStatus.INACTIVE && sw.getSwitchId() == alphaDatapath));
    }

    @Test
    public void switchFromHistoryToOfflineToOnlineRemovedPort() {

        // History

        HistoryFacts history = new HistoryFacts(alphaDatapath);

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

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchAddWithHistory(history);

        // Online

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        resetMocks();

        service.switchEvent(switchAddEvent);

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 3));

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

    }

    @Test
    public void switchFromOnlineToOfflineToOnline() {

        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);

        service.switchEvent(switchAddEvent);


        SwitchInfoData deactivatedSwitch = switchAddEvent.toBuilder().state(SwitchChangeType.DEACTIVATED).build();

        service.switchEvent(deactivatedSwitch);

        List<SpeakerSwitchPortView> ports2 = getSpeakerSwitchPortViewsRevert();

        SpeakerSwitchView speakerSwitchView2 = getSpeakerSwitchView().toBuilder()
                .ports(ports2)
                .build();

        SwitchInfoData switchAddEvent2 = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView2);

        resetMocks();

        service.switchEvent(switchAddEvent2);

        // System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), true);

        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports2.get(2).getNumber()),
                                        LinkStatus.of(ports2.get(2).getState()));
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, ports2.get(3).getNumber()),
                                           LinkStatus.of(ports2.get(3).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports2.get(0).getNumber()),
                                        LinkStatus.of(ports2.get(0).getState()));
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, ports2.get(1).getNumber()),
                                           LinkStatus.of(ports2.get(0).getState()));
    }

    @Test
    public void switchFromOnlineToOnline() {
        List<SpeakerSwitchPortView> ports = Lists.newArrayList(
                new SpeakerSwitchPortView(1, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(2, SpeakerSwitchPortView.State.DOWN),
                new SpeakerSwitchPortView(2 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN),

                new SpeakerSwitchPortView(3, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(3 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(4, SpeakerSwitchPortView.State.DOWN),
                new SpeakerSwitchPortView(4 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN));

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ImmutableList.copyOf(ports))
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                BFD_LOGICAL_PORT_OFFSET);

        // initial switch ADD
        service.switchEvent(switchAddEvent);

        resetMocks();

        // periodic network sync (swap UP/DOWN state for half of the ports)
        for (int idx = 0; idx < 4 && idx < ports.size(); idx++) {
            ports.set(idx, makePortEntryWithOppositeState(ports.get(idx)));
        }

        SpeakerSwitchView periodicSyncEvent = speakerSwitchView.toBuilder().ports(ImmutableList.copyOf(ports)).build();
        service.switchBecomeManaged(periodicSyncEvent);

        // only changed ports
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 1), LinkStatus.DOWN);
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.DOWN);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 2), LinkStatus.UP);
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.UP);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void switchFromOnlineToOnlineWithLostBfdFeature() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                BFD_LOGICAL_PORT_OFFSET);

        List<SpeakerSwitchPortView> ports = doSpeakerOnline(service, Collections.singleton(Feature.BFD));
        List<SpeakerSwitchPortView> ports2 = swapBfdPortsState(ports);

        resetMocks();

        service.switchBecomeManaged(getSpeakerSwitchView().toBuilder()
                .features(Collections.emptySet())
                .ports(ports2)
                .build());

        // System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).removeBfdPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), null);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), true);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.DOWN);

        verify(carrier).removeBfdPortHandler(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), null);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), true);
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.UP);
    }

    @Test
    public void switchFromOnlineToOnlineWithAcquireBfdFeature() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                BFD_LOGICAL_PORT_OFFSET);

        List<SpeakerSwitchPortView> ports = doSpeakerOnline(service, Collections.emptySet());
        List<SpeakerSwitchPortView> ports2 = swapBfdPortsState(ports);

        resetMocks();

        service.switchBecomeManaged(getSpeakerSwitchView().toBuilder()
                .features(Collections.singleton(Feature.BFD))
                .ports(ports2)
                .build());

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).setupBfdPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), 1);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), true);
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.DOWN);

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET));
        verify(carrier).setupBfdPortHandler(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), 2);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), true);
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, 2 + BFD_LOGICAL_PORT_OFFSET), LinkStatus.UP);
    }

    @Test
    public void portAddEventOnOnlineSwitch() {
        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                getSpeakerSwitchView());
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.ADD));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.ADD));

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, 1), null);
        verify(carrier).setupBfdPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), 1);

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, 1), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET), true);
    }

    @Test
    public void portUpDownEventsOnOnlineSwitch() {

        List<SpeakerSwitchPortView> portsDown = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.DOWN),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.DOWN));

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(portsDown)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);
        resetMocks();
        //System.out.println(mockingDetails(carrier).printInvocations());

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.UP));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.UP));
        List<SpeakerSwitchPortView> portsUp = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.UP),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.UP));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, portsUp.get(0).getNumber()),
                                        LinkStatus.of(portsUp.get(0).getState()));
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, portsUp.get(1).getNumber()),
                                           LinkStatus.of(portsUp.get(1).getState()));

        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.DOWN));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.DOWN));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, portsDown.get(0).getNumber()),
                                        LinkStatus.of(portsDown.get(0).getState()));
        verify(carrier).setBfdPortLinkMode(Endpoint.of(alphaDatapath, portsDown.get(1).getNumber()),
                                           LinkStatus.of(portsDown.get(1).getState()));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void portAddOnOnlineSwitch() {
        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);

        // prepare
        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(Collections.emptyList())
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        service.switchEvent(switchAddEvent);

        // process
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);
        PortInfoData speakerPortEvent = new PortInfoData(endpoint.getDatapath(), endpoint.getPortNumber(),
                                                         PortChangeType.ADD, true);

        service.switchPortEvent(speakerPortEvent);

        verify(carrier).setupPortHandler(endpoint, null);
        verify(carrier).setOnlineMode(endpoint, true);
        verify(carrier).setPortLinkMode(endpoint, LinkStatus.UP);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void portDelEventOnOnlineSwitch() {

        List<SpeakerSwitchPortView> portsDown = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.DOWN),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.DOWN));

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(portsDown)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.DELETE));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.DELETE));

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 1));
        verify(carrier).removeBfdPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void switchWithNoBfdSupport() {
        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();

        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .ports(ports)
                .features(Collections.emptySet())
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        NetworkSwitchService service = new NetworkSwitchService(carrier, persistenceManager,
                                                                BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);

        // System.out.println(mockingDetails(carrier).printInvocations());
        // System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), null);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), null);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), null);
        verify(carrier).setupPortHandler(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), null);

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), true);

        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()),
                                        LinkStatus.of(ports.get(2).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()),
                                        LinkStatus.of(ports.get(3).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()),
                                        LinkStatus.of(ports.get(0).getState()));
        verify(carrier).setPortLinkMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()),
                                        LinkStatus.of(ports.get(0).getState()));
    }

    private List<SpeakerSwitchPortView> doSpeakerOnline(NetworkSwitchService service, Set<Feature> features) {
        List<SpeakerSwitchPortView> ports = getSpeakerSwitchPortViews();
        SpeakerSwitchView speakerSwitchView = getSpeakerSwitchView().toBuilder()
                .features(features)
                .ports(ports)
                .build();

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        service.switchEvent(switchAddEvent);

        return ports;
    }

    private SpeakerSwitchView getSpeakerSwitchView() {
        return SpeakerSwitchView.builder()
                    .datapath(alphaDatapath)
                    .switchSocketAddress(alphaInetAddress)
                    .speakerSocketAddress(speakerInetAddress)
                    .ofVersion("OF_13")
                    .description(switchDescription)
                    .features(Collections.singleton(Feature.BFD))
                    .build();
    }

    private List<SpeakerSwitchPortView> getSpeakerSwitchPortViews() {
        return ImmutableList.of(
                    new SpeakerSwitchPortView(1, SpeakerSwitchPortView.State.UP),
                    new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                    new SpeakerSwitchPortView(2, SpeakerSwitchPortView.State.DOWN),
                    new SpeakerSwitchPortView(2 + BFD_LOGICAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN));
    }

    private List<SpeakerSwitchPortView> getSpeakerSwitchPortViewsRevert() {
        return ImmutableList.of(
                new SpeakerSwitchPortView(1, State.DOWN),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.DOWN),
                new SpeakerSwitchPortView(2, State.UP),
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
}
