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
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.facts.BfdPortFacts;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final int BFD_LOCAL_PORT_OFFSET = 200;


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

    private final String alphaDescription = String.format("%s OF_13 %s",
            switchDescription.getManufacturer(),
            switchDescription.getSoftware());


    public DiscoverySwitchServiceTest() throws UnknownHostException {
    }


    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void newSwitch() {

        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);

        doAnswer((Answer) invocation -> {
            TransactionCallbackWithoutResult tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));

        Set<Feature> features = new HashSet<>();
        features.add(SpeakerSwitchView.Feature.BFD);

        List<SpeakerSwitchPortView> ports = ImmutableList.of(
                new SpeakerSwitchPortView(1, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(1 + BFD_LOCAL_PORT_OFFSET, SpeakerSwitchPortView.State.UP),
                new SpeakerSwitchPortView(2, SpeakerSwitchPortView.State.DOWN),
                new SpeakerSwitchPortView(2 + BFD_LOCAL_PORT_OFFSET, SpeakerSwitchPortView.State.DOWN));


        SpeakerSwitchView speakerSwitchView = new SpeakerSwitchView(
                alphaDatapath, alphaInetAddress, speakerInetAddress, "OF_13", switchDescription, features,
                ports);

        SwitchInfoData switchAddEvent = new SwitchInfoData(
                alphaDatapath, SwitchChangeType.ACTIVATED,
                alphaInetAddress.toString(), alphaInetAddress.toString(), alphaDescription,
                speakerInetAddress.toString(),
                false,
                speakerSwitchView);

        DiscoverySwitchService service = new DiscoverySwitchService(carrier, persistenceManager, BFD_LOCAL_PORT_OFFSET);
        service.switchEvent(switchAddEvent);

        System.out.println(mockingDetails(carrier).printInvocations());
        System.out.println(mockingDetails(switchRepository).printInvocations());

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
}
