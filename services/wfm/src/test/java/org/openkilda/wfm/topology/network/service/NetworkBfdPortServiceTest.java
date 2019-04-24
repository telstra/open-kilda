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
import org.openkilda.model.BfdSession;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.BfdSessionRepository;
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
    private final String alphaAddress = "192.168.1.1";
    private final String betaAddress = "192.168.1.2";

    private final Switch alphaSwitch = Switch.builder()
            .switchId(alphaEndpoint.getDatapath())
            .address(alphaAddress)
            .build();
    private final Switch betaSwitch = Switch.builder()
            .switchId(betaEndpoint.getDatapath())
            .address(betaAddress)
            .build();

    private final String removeCallKey = "bfd-remove-speaker-key";

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private SwitchRepository switchRepository;

    @Mock
    private BfdSessionRepository bfdSessionRepository;

    @Mock
    private IBfdPortCarrier carrier;

    private NetworkBfdPortService service;

    @Before
    public void setUp() throws Exception {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);

        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createBfdSessionRepository()).thenReturn(bfdSessionRepository);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        service = new NetworkBfdPortService(carrier, persistenceManager);
    }

    @Test
    public void enableDisable() throws UnknownHostException {
        when(bfdSessionRepository.findBySwitchIdAndPort(alphaEndpoint.getDatapath(), alphaEndpoint.getPortNumber()))
                .thenReturn(Optional.empty());
        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);

        service.setup(alphaLogicalEndpoint, alphaEndpoint.getPortNumber());

        reset(bfdSessionRepository);

        // setup BFD session
        IslReference islReference = new IslReference(alphaEndpoint, betaEndpoint);
        service.enable(alphaEndpoint, islReference);

        service.updateLinkStatus(alphaLogicalEndpoint, LinkStatus.UP);

        verify(bfdSessionRepository).findBySwitchIdAndPort(
                alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber());

        ArgumentCaptor<BfdSession> bfdSessionCreateArgument = ArgumentCaptor.forClass(BfdSession.class);
        verify(bfdSessionRepository).createOrUpdate(bfdSessionCreateArgument.capture());

        BfdSession bfdSessionDb = bfdSessionCreateArgument.getValue();
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
        verifyNoMoreInteractions(bfdSessionRepository);

        reset(carrier);
        reset(bfdSessionRepository);

        // remove BFD session
        when(carrier.removeBfdSession(any(NoviBfdSession.class))).thenReturn(removeCallKey);

        service.disable(alphaEndpoint);

        verify(carrier).bfdKillNotification(alphaEndpoint);

        ArgumentCaptor<NoviBfdSession> bfdSessionRemoveSpeakerArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).removeBfdSession(bfdSessionRemoveSpeakerArgument.capture());

        NoviBfdSession speakerBfdRemove = bfdSessionRemoveSpeakerArgument.getValue();
        Assert.assertEquals(speakerBfdSetup, speakerBfdRemove);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);

        reset(carrier);

        // remove confirmation

        when(bfdSessionRepository.findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                     alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(bfdSessionDb));

        BfdSessionResponse speakerResponse = new BfdSessionResponse(speakerBfdRemove, null);
        service.speakerResponse(removeCallKey, alphaLogicalEndpoint, speakerResponse);
        service.updateLinkStatus(alphaLogicalEndpoint, LinkStatus.DOWN);

        verify(bfdSessionRepository).findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                        alphaLogicalEndpoint.getPortNumber());
        verify(bfdSessionRepository).delete(bfdSessionDb);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);
    }

    @Test
    public void distinguishRecoverableErrors() {
        // prepare DB record to force cleanup on start
        BfdSession initialBfdSession = makeBfdSession(1);
        NoviBfdSession removeRequestPayload = forceCleanupAfterInit(initialBfdSession);

        // push speaker error response
        mockBfdSessionLookup(initialBfdSession);
        BfdSessionResponse removeResponse = new BfdSessionResponse(
                removeRequestPayload, NoviBfdSession.Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR);
        service.speakerResponse(removeCallKey, alphaLogicalEndpoint, removeResponse);

        verify(bfdSessionRepository).findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                           alphaLogicalEndpoint.getPortNumber());
        verify(bfdSessionRepository).delete(initialBfdSession);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);

        reset(carrier);
        reset(bfdSessionRepository);

        // following ENABLE request must initiate INSTALL process

        when(bfdSessionRepository.findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                        alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.empty());
        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);

        service.enable(alphaEndpoint, new IslReference(alphaEndpoint, betaEndpoint));

        verify(carrier).setupBfdSession(any(NoviBfdSession.class));
    }

    @Test
    public void failOnCriticalErrors() {
        BfdSession initialBfdSession = makeBfdSession(1);
        NoviBfdSession removeRequestPayload = forceCleanupAfterInit(initialBfdSession);

        // push speaker error(critical) response
        mockBfdSessionLookup(initialBfdSession);
        BfdSessionResponse removeResponse = new BfdSessionResponse(
                removeRequestPayload, NoviBfdSession.Errors.SWITCH_RESPONSE_ERROR);
        service.speakerResponse(removeCallKey, alphaLogicalEndpoint, removeResponse);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);

        reset(carrier);
        reset(bfdSessionRepository);

        // following ENABLE request(s) must be ignored
        service.enable(alphaEndpoint, new IslReference(alphaEndpoint, betaEndpoint));
        verifyNoMoreInteractions(carrier);
    }

    private BfdSession makeBfdSession(Integer discriminator) {
        return BfdSession.builder(alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber())
                .ipAddress(alphaAddress)
                .remoteSwitchId(betaLogicalEndpoint.getDatapath())
                .remoteIpAddress(betaAddress)
                .discriminator(discriminator)
                .build();
    }

    private NoviBfdSession forceCleanupAfterInit(BfdSession initialBfdSession) {
        when(bfdSessionRepository.findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                        alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(initialBfdSession));
        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);

        when(carrier.removeBfdSession(any(NoviBfdSession.class))).thenReturn(removeCallKey);

        service.setup(alphaLogicalEndpoint, alphaEndpoint.getPortNumber());

        ArgumentCaptor<NoviBfdSession> setupBfdSessionArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).removeBfdSession(setupBfdSessionArgument.capture());

        reset(carrier);
        reset(bfdSessionRepository);

        return setupBfdSessionArgument.getValue();
    }

    private void mockSwitchLookup(Switch sw) {
        when(switchRepository.findById(sw.getSwitchId())).thenReturn(Optional.ofNullable(sw));
    }

    private void mockBfdSessionLookup(BfdSession session) {
        when(bfdSessionRepository.findBySwitchIdAndPort(session.getSwitchId(), session.getPort()))
                .thenReturn(Optional.of(session));
    }
}
