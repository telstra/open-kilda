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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.model.BfdPort;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.BfdPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.LinkStatus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class NetworkBfdPortServiceTest {
    private static final int BFD_LOGICAL_PORT_OFFSET = 200;

    private final Endpoint alphaEndpoint = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint alphaLogicalEndpoint = Endpoint.of(alphaEndpoint.getDatapath(),
                                                              alphaEndpoint.getPortNumber() + BFD_LOGICAL_PORT_OFFSET);
    private final Endpoint betaEndpoint = Endpoint.of(new SwitchId(2), 2);
    private final Endpoint betaLogicalEndpoint = Endpoint.of(betaEndpoint.getDatapath(),
                                                             betaEndpoint.getPortNumber() + BFD_LOGICAL_PORT_OFFSET);

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private SwitchRepository switchRepository;

    @Mock
    private BfdPortRepository bfdPortRepository;

    @Mock
    private IBfdPortCarrier carrier;

    private NetworkBfdPortService service;

    @Before
    public void setUp() throws Exception {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);

        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createBfdPortRepository()).thenReturn(bfdPortRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new NetworkBfdPortService(carrier, persistenceManager);
    }

    @Test
    public void enableDisable() throws UnknownHostException {
        String alphaAddress = "192.168.1.1";
        String betaAddress = "192.168.1.2";
        final String removeCallKey = "bfd-remove-speaker-key";

        when(bfdPortRepository.findBySwitchIdAndPort(alphaEndpoint.getDatapath(), alphaEndpoint.getPortNumber()))
                .thenReturn(Optional.empty());
        when(switchRepository.findById(alphaEndpoint.getDatapath())).thenReturn(
                Optional.of(Switch.builder()
                                    .switchId(alphaEndpoint.getDatapath())
                                    .address(alphaAddress)
                                    .build()));
        when(switchRepository.findById(betaEndpoint.getDatapath())).thenReturn(
                Optional.of(Switch.builder()
                                    .switchId(betaEndpoint.getDatapath())
                                    .address(betaAddress)
                                    .build()));

        service.setup(alphaLogicalEndpoint, alphaEndpoint.getPortNumber());

        reset(bfdPortRepository);

        // setup BFD session
        IslReference islReference = new IslReference(alphaEndpoint, betaEndpoint);
        service.enable(alphaEndpoint, islReference);

        service.updateLinkStatus(alphaLogicalEndpoint, LinkStatus.UP);

        verify(bfdPortRepository).findBySwitchIdAndPort(
                alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber());

        ArgumentCaptor<BfdPort> bfdSessionCreateArgument = ArgumentCaptor.forClass(BfdPort.class);
        verify(bfdPortRepository).createOrUpdate(bfdSessionCreateArgument.capture());

        BfdPort bfdSessionDb = bfdSessionCreateArgument.getValue();
        Assert.assertEquals(alphaLogicalEndpoint.getDatapath(), bfdSessionDb.getSwitchId());
        Assert.assertEquals((Integer) alphaLogicalEndpoint.getPortNumber(), bfdSessionDb.getPort());
        Assert.assertNotNull(bfdSessionDb.getDiscriminator());

        ArgumentCaptor<NoviBfdSession> bfdSessionCreateSpeakerArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).setupBfdSession(bfdSessionCreateSpeakerArgument.capture());

        NoviBfdSession speakerBfdSetup = bfdSessionCreateSpeakerArgument.getValue();
        Assert.assertEquals(alphaEndpoint.getDatapath(), speakerBfdSetup.getTarget().getDatapath());
        Assert.assertEquals(InetAddress.getByName(alphaAddress), speakerBfdSetup.getTarget().getInetAddress());
        Assert.assertEquals(betaEndpoint.getDatapath(), speakerBfdSetup.getRemote().getDatapath());
        Assert.assertEquals(InetAddress.getByName(betaAddress), speakerBfdSetup.getRemote().getInetAddress());
        Assert.assertEquals(alphaEndpoint.getPortNumber(), speakerBfdSetup.getPhysicalPortNumber());
        Assert.assertEquals(alphaLogicalEndpoint.getPortNumber(), speakerBfdSetup.getLogicalPortNumber());
        Assert.assertEquals(bfdSessionDb.getDiscriminator(), (Integer) speakerBfdSetup.getDiscriminator());
        Assert.assertTrue(speakerBfdSetup.isKeepOverDisconnect());

        verify(carrier).bfdUpNotification(alphaEndpoint);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdPortRepository);

        reset(carrier);
        reset(bfdPortRepository);

        // remove BFD session
        when(carrier.removeBfdSession(any(NoviBfdSession.class))).thenReturn(removeCallKey);

        service.disable(alphaEndpoint);

        verify(carrier).bfdKillNotification(alphaEndpoint);

        ArgumentCaptor<NoviBfdSession> bfdSessionRemoveSpeakerArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).removeBfdSession(bfdSessionRemoveSpeakerArgument.capture());

        NoviBfdSession speakerBfdRemove = bfdSessionRemoveSpeakerArgument.getValue();
        Assert.assertEquals(speakerBfdSetup, speakerBfdRemove);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdPortRepository);

        reset(carrier);

        // remove confirmation

        when(bfdPortRepository.findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                     alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(bfdSessionDb));

        BfdSessionResponse speakerResponse = new BfdSessionResponse(speakerBfdRemove, null);
        service.speakerResponse(removeCallKey, alphaLogicalEndpoint, speakerResponse);
        service.updateLinkStatus(alphaLogicalEndpoint, LinkStatus.DOWN);

        verify(bfdPortRepository).findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                        alphaLogicalEndpoint.getPortNumber());
        verify(bfdPortRepository).delete(bfdSessionDb);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdPortRepository);
    }
}
