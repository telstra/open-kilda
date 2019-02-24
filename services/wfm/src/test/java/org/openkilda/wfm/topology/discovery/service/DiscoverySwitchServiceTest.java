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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
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
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;
import org.openkilda.wfm.topology.discovery.model.facts.HistoryFacts;
import org.openkilda.wfm.topology.discovery.model.facts.PortFacts;

import com.google.common.collect.ImmutableList;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class DiscoverySwitchServiceTest {

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


    public DiscoverySwitchServiceTest() throws UnknownHostException {
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

        reset(switchRepository);

        reset(repositoryFactory);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
                                                                    BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(new PortFacts(alphaDatapath, ports.get(0)), null);
        verify(carrier).setupBfdPortHandler(new BfdPortFacts(new PortFacts(alphaDatapath, ports.get(1)), 1));
        verify(carrier).setupPortHandler(new PortFacts(alphaDatapath, ports.get(2)), null);
        verify(carrier).setupBfdPortHandler(new BfdPortFacts(new PortFacts(alphaDatapath, ports.get(3)), 2));

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(0).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(1).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports.get(2).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), true);

        verify(carrier).setPortLinkMode(new PortFacts(alphaDatapath, ports.get(2)));
        verify(carrier).setBfdPortLinkMode(new PortFacts(alphaDatapath, ports.get(3)));
        verify(carrier).setPortLinkMode(new PortFacts(alphaDatapath, ports.get(0)));
        verify(carrier).setBfdPortLinkMode(new PortFacts(alphaDatapath, ports.get(1)));

        verify(switchRepository).createOrUpdate(argThat(sw ->
                sw.getStatus() == SwitchStatus.ACTIVE && sw.getSwitchId() == alphaDatapath));
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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
                                                                    BFD_LOGICAL_PORT_OFFSET);
        service.switchAddWithHistory(history);

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setupPortHandler(new PortFacts(Endpoint.of(alphaDatapath, 1)), islAtoB);
        verify(carrier).setupPortHandler(new PortFacts(Endpoint.of(alphaDatapath, 2)), islAtoB2);
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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
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

        //System.out.println(mockingDetails(carrier).printInvocations());
        //System.out.println(mockingDetails(switchRepository).printInvocations());

        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports2.get(0).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports2.get(1).getNumber()), true);
        verify(carrier).setOnlineMode(Endpoint.of(alphaDatapath, ports2.get(2).getNumber()), true);
        verify(carrier).setBfdPortOnlineMode(Endpoint.of(alphaDatapath, ports.get(3).getNumber()), true);

        verify(carrier).setPortLinkMode(new PortFacts(alphaDatapath, ports2.get(2)));
        verify(carrier).setBfdPortLinkMode(new PortFacts(alphaDatapath, ports2.get(3)));
        verify(carrier).setPortLinkMode(new PortFacts(alphaDatapath, ports2.get(0)));
        verify(carrier).setBfdPortLinkMode(new PortFacts(alphaDatapath, ports2.get(1)));

    }


    @Test
    public void portAddEventOnOnlineSwitch() {
        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                getSpeakerSwitchView());
        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
                                                                    BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.ADD));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.ADD));

        //System.out.println(mockingDetails(carrier).printInvocations());

        verify(carrier).setupPortHandler(new PortFacts(Endpoint.of(alphaDatapath, 1)), null);
        verify(carrier).setupBfdPortHandler(
                new BfdPortFacts(new PortFacts(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET)), 1));

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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
                                                                    BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);
        resetMocks();
        //System.out.println(mockingDetails(carrier).printInvocations());

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.UP));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.UP));
        List<SpeakerSwitchPortView> portsUp = ImmutableList.of(
                new SpeakerSwitchPortView(1, State.UP),
                new SpeakerSwitchPortView(1 + BFD_LOGICAL_PORT_OFFSET, State.UP));
        verify(carrier).setPortLinkMode(new PortFacts(alphaDatapath, portsUp.get(0)));
        verify(carrier).setBfdPortLinkMode(new PortFacts(alphaDatapath, portsUp.get(1)));

        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.DOWN));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.DOWN));
        verify(carrier).setPortLinkMode(new PortFacts(alphaDatapath, portsDown.get(0)));
        verify(carrier).setBfdPortLinkMode(new PortFacts(alphaDatapath, portsDown.get(1)));

        //System.out.println(mockingDetails(carrier).printInvocations());
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

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager,
                                                                    BFD_LOGICAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);
        resetMocks();

        service.switchPortEvent(new PortInfoData(alphaDatapath, 1, PortChangeType.DELETE));
        service.switchPortEvent(new PortInfoData(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET, PortChangeType.DELETE));

        verify(carrier).removePortHandler(Endpoint.of(alphaDatapath, 1));
        verify(carrier).removeBfdPortHandler(Endpoint.of(alphaDatapath, 1 + BFD_LOGICAL_PORT_OFFSET));

        //System.out.println(mockingDetails(carrier).printInvocations());
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
}
