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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.floodlight.response.BfdSessionResponse;
import org.openkilda.messaging.model.NoviBfdSession;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.BfdSession;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionCallback;
import org.openkilda.persistence.tx.TransactionCallbackWithoutResult;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.model.BfdSessionData;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.utils.EndpointStatusMonitor;
import org.openkilda.wfm.topology.network.utils.SwitchOnlineStatusMonitor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class NetworkBfdSessionServiceTest {
    private static final int BFD_LOGICAL_PORT_OFFSET = 200;

    private final Endpoint alphaEndpoint = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint alphaLogicalEndpoint = Endpoint.of(alphaEndpoint.getDatapath(),
                                                              alphaEndpoint.getPortNumber() + BFD_LOGICAL_PORT_OFFSET);
    private final Endpoint betaEndpoint = Endpoint.of(new SwitchId(2), 2);
    private final Endpoint betaLogicalEndpoint = Endpoint.of(betaEndpoint.getDatapath(),
                                                             betaEndpoint.getPortNumber() + BFD_LOGICAL_PORT_OFFSET);

    private final Endpoint gammaEndpoint = Endpoint.of(new SwitchId(3), 3);

    private final IslReference alphaToBetaIslRef = new IslReference(alphaEndpoint, betaEndpoint);
    private final BfdProperties genericBfdProperties = BfdProperties.builder()
            .interval(Duration.ofMillis(350))
            .multiplier((short) 3)
            .build();

    private final String alphaAddress = "192.168.1.1";
    private final String betaAddress = "192.168.1.2";
    private final String gammaAddress = "192.168.1.3";

    private final Switch alphaSwitch = Switch.builder()
            .switchId(alphaEndpoint.getDatapath())
            .socketAddress(getSocketAddress(alphaAddress, 30070))
            .build();
    private final Switch betaSwitch = Switch.builder()
            .switchId(betaEndpoint.getDatapath())
            .socketAddress(getSocketAddress(betaAddress, 30071))
            .build();
    private final Switch gammaSwitch = Switch.builder()
            .switchId(gammaEndpoint.getDatapath())
            .socketAddress(getSocketAddress(gammaAddress, 30072))
            .build();

    private final String setupRequestKey = "bfd-setup-speaker-key";
    private final String removeRequestKey = "bfd-remove-speaker-key";

    private SwitchOnlineStatusMonitor switchOnlineStatusMonitor;
    private EndpointStatusMonitor endpointStatusMonitor;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private SwitchRepository switchRepository;

    @Mock
    private BfdSessionRepository bfdSessionRepository;

    @Mock
    private IBfdSessionCarrier carrier;

    private NetworkBfdSessionService service;

    @Before
    public void setUp() throws Exception {
        RepositoryFactory repositoryFactory = Mockito.mock(RepositoryFactory.class);

        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createBfdSessionRepository()).thenReturn(bfdSessionRepository);
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        doAnswer(invocation -> {
            TransactionCallbackWithoutResult<?> tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));
        doAnswer(invocation -> {
            TransactionCallback<?, ?> tr = invocation.getArgument(0);
            return tr.doInTransaction();
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallback.class));
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        switchOnlineStatusMonitor = new SwitchOnlineStatusMonitor();
        endpointStatusMonitor = new EndpointStatusMonitor();

        setupCarrier();

        service = new NetworkBfdSessionService(
                persistenceManager, switchOnlineStatusMonitor, endpointStatusMonitor, carrier);
    }

    @Test
    public void enableDisable() throws UnknownHostException {
        when(bfdSessionRepository.findBySwitchIdAndPort(
                alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.empty());
        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);

        doAnswer(invocation -> invocation.getArgument(0))
                .when(bfdSessionRepository).add(any());

        // handle enable/update request
        when(carrier.sendWorkerBfdSessionCreateRequest(any(NoviBfdSession.class))).thenReturn(setupRequestKey);
        switchOnlineStatusMonitor.update(alphaEndpoint.getDatapath(), true);

        service.enableUpdate(
                alphaLogicalEndpoint, alphaEndpoint.getPortNumber(),
                new BfdSessionData(alphaToBetaIslRef, genericBfdProperties));

        ArgumentCaptor<NoviBfdSession> createBfdSessionRequestCaptor = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionCreateRequest(createBfdSessionRequestCaptor.capture());
        NoviBfdSession setupBfdSessionPayload = createBfdSessionRequestCaptor.getValue();

        ArgumentCaptor<BfdSession> bfdSessionCreateArgument = ArgumentCaptor.forClass(BfdSession.class);
        verify(bfdSessionRepository).add(bfdSessionCreateArgument.capture());

        BfdSession bfdSessionDb = bfdSessionCreateArgument.getValue();
        Assert.assertNotNull(bfdSessionDb.getDiscriminator());
        Assert.assertNull(bfdSessionDb.getInterval());
        Assert.assertEquals(Short.valueOf((short) 0), bfdSessionDb.getMultiplier());

        // speaker response
        when(bfdSessionRepository.findBySwitchIdAndPort(
                alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(bfdSessionCreateArgument.getValue()));
        service.speakerResponse(alphaLogicalEndpoint, setupRequestKey, new BfdSessionResponse(
                setupBfdSessionPayload, null));
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);

        verify(bfdSessionRepository).add(bfdSessionCreateArgument.capture());
        bfdSessionDb = bfdSessionCreateArgument.getValue();

        Assert.assertEquals(alphaLogicalEndpoint.getDatapath(), bfdSessionDb.getSwitchId());
        Assert.assertEquals((Integer) alphaLogicalEndpoint.getPortNumber(), bfdSessionDb.getPort());
        Assert.assertNotNull(bfdSessionDb.getDiscriminator());

        ArgumentCaptor<NoviBfdSession> bfdSessionCreateSpeakerArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionCreateRequest(bfdSessionCreateSpeakerArgument.capture());

        NoviBfdSession speakerBfdSetup = bfdSessionCreateSpeakerArgument.getValue();
        Assert.assertEquals(alphaEndpoint.getDatapath(), speakerBfdSetup.getTarget().getDatapath());
        Assert.assertEquals(InetAddress.getByName(alphaAddress), speakerBfdSetup.getTarget().getInetAddress());
        Assert.assertEquals(betaEndpoint.getDatapath(), speakerBfdSetup.getRemote().getDatapath());
        Assert.assertEquals(InetAddress.getByName(betaAddress), speakerBfdSetup.getRemote().getInetAddress());
        Assert.assertEquals(alphaEndpoint.getPortNumber(), speakerBfdSetup.getPhysicalPortNumber());
        Assert.assertEquals(alphaLogicalEndpoint.getPortNumber(), speakerBfdSetup.getLogicalPortNumber());
        Assert.assertEquals(bfdSessionDb.getDiscriminator(), (Integer) speakerBfdSetup.getDiscriminator());
        Assert.assertEquals(genericBfdProperties.getInterval(), bfdSessionDb.getInterval());
        Assert.assertEquals(Short.valueOf(genericBfdProperties.getMultiplier()), bfdSessionDb.getMultiplier());
        Assert.assertTrue(speakerBfdSetup.isKeepOverDisconnect());

        verify(carrier).bfdUpNotification(alphaEndpoint);

        verifyNoMoreInteractions(carrier);

        resetCarrier();
        reset(bfdSessionRepository);

        // remove BFD session
        when(carrier.sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class))).thenReturn(removeRequestKey);

        service.disable(alphaEndpoint);

        verify(carrier).bfdKillNotification(alphaEndpoint);

        ArgumentCaptor<NoviBfdSession> bfdSessionRemoveSpeakerArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionDeleteRequest(bfdSessionRemoveSpeakerArgument.capture());

        NoviBfdSession speakerBfdRemove = bfdSessionRemoveSpeakerArgument.getValue();
        Assert.assertEquals(speakerBfdSetup, speakerBfdRemove);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);

        resetCarrier();

        // remove confirmation

        when(bfdSessionRepository.findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                        alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(bfdSessionDb));

        BfdSessionResponse speakerResponse = new BfdSessionResponse(speakerBfdRemove, null);
        service.speakerResponse(alphaLogicalEndpoint, removeRequestKey, speakerResponse);

        verify(carrier).sessionRotateRequest(alphaLogicalEndpoint, false);
        verify(carrier).sessionCompleteNotification(alphaEndpoint);
        verify(bfdSessionRepository).findBySwitchIdAndPort(
                alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber());
        verify(bfdSessionRepository).remove(bfdSessionDb);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);
    }

    @Test
    public void distinguishRecoverableErrors() {
        // prepare DB record to force cleanup on start
        BfdSession initialBfdSession = makeBfdSession(1);
        NoviBfdSession removeRequestPayload = forceCleanupAfterInit(initialBfdSession);

        // push speaker error response
        when(bfdSessionRepository.findBySwitchIdAndPort(initialBfdSession.getSwitchId(), initialBfdSession.getPort()))
                .thenReturn(Optional.of(initialBfdSession))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(bfdSessionRepository).add(any());

        BfdSessionResponse removeResponse = new BfdSessionResponse(
                removeRequestPayload, NoviBfdSession.Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR);
        // complete cleanup and make session create request
        service.speakerResponse(alphaLogicalEndpoint, removeRequestKey, removeResponse);

        verify(bfdSessionRepository, atLeastOnce())
                .findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber());
        verify(bfdSessionRepository).remove(initialBfdSession);
        verify(bfdSessionRepository).add(any(BfdSession.class));

        verify(carrier).sessionRotateRequest(alphaLogicalEndpoint, false);
        verify(carrier).sendWorkerBfdSessionCreateRequest(any(NoviBfdSession.class));

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);
    }

    @Test
    public void failOnCriticalErrors() {
        BfdSession initialBfdSession = makeBfdSession(1);
        NoviBfdSession removeRequestPayload = forceCleanupAfterInit(initialBfdSession);

        // push speaker error(critical) response
        mockBfdSessionLookup(initialBfdSession);
        BfdSessionResponse removeResponse = new BfdSessionResponse(
                removeRequestPayload, NoviBfdSession.Errors.SWITCH_RESPONSE_ERROR);
        service.speakerResponse(alphaLogicalEndpoint, removeRequestKey, removeResponse);

        verify(carrier).bfdFailNotification(alphaEndpoint);

        verifyNoMoreInteractions(carrier);
        verifyNoMoreInteractions(bfdSessionRepository);

        resetCarrier();
        reset(bfdSessionRepository);

        // make one more remove attempt on next enable/update request
        service.enableUpdate(
                alphaLogicalEndpoint, alphaEndpoint.getPortNumber(),
                new BfdSessionData(alphaToBetaIslRef, genericBfdProperties));
        verify(carrier).sendWorkerBfdSessionDeleteRequest(removeRequestPayload);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void upDownUp() {
        // up
        createOperationalSession();

        // down
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.DOWN);
        verify(carrier).bfdDownNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        resetCarrier();

        // up
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);
        verify(carrier).bfdUpNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void upOfflineUp() {
        // up
        createOperationalSession();

        // offline
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), false);
        verify(carrier).bfdKillNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        resetCarrier();

        // online (up)
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);
        verify(carrier).bfdUpNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void upOfflineDownUp() {
        // up
        createOperationalSession();

        // offline
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), false);
        verify(carrier).bfdKillNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        resetCarrier();

        // online (down)
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.DOWN);
        verify(carrier).bfdDownNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        // up
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);
        verify(carrier).bfdUpNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void upDownOfflineUp() {
        // up
        createOperationalSession();

        // down
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.DOWN);
        verify(carrier).bfdDownNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        resetCarrier();

        // offline
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), false);
        verify(carrier).bfdKillNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        resetCarrier();

        // up
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);
        verify(carrier).bfdUpNotification(alphaEndpoint);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void surviveOffline() {
        // offline
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), false);

        doAnswer(invocation -> invocation.getArgument(0))
                .when(bfdSessionRepository).add(any());
        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);
        mockMissingBfdSession(alphaLogicalEndpoint);

        service.enableUpdate(
                alphaLogicalEndpoint, alphaEndpoint.getPortNumber(),
                new BfdSessionData(alphaToBetaIslRef, genericBfdProperties));

        verifyNoMoreInteractions(carrier);

        // online
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);
        // ensure we react on enable requests
        verify(carrier).sendWorkerBfdSessionCreateRequest(argThat(
                argument -> argument.getTarget().getDatapath().equals(alphaLogicalEndpoint.getDatapath())
                && argument.getRemote().getDatapath().equals(betaEndpoint.getDatapath())));
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void enableOfflineDisableOnline() {
        createOperationalSession();

        // offline
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), false);
        verify(carrier).bfdKillNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);
        resetCarrier();

        // disable
        service.disable(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        // online
        when(carrier.sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class)))
                .thenReturn(removeRequestKey);
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);
        verify(carrier).sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class));
        verifyNoMoreInteractions(carrier);
        resetCarrier();
    }

    @Test
    public void offlineDuringCleaning() {
        createOperationalSession();

        String requestKey = "request-key-#";

        // disable
        when(carrier.sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class))).thenReturn(requestKey + "1");

        service.disable(alphaEndpoint);

        ArgumentCaptor<NoviBfdSession> removeBfdSessionArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionDeleteRequest(removeBfdSessionArgument.capture());

        resetCarrier();

        // offline
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), false);
        verifyNoMoreInteractions(carrier);
        resetCarrier();

        // online
        when(carrier.sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class))).thenReturn(requestKey + "2");

        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.DOWN);

        verify(carrier).sendWorkerBfdSessionDeleteRequest(removeBfdSessionArgument.getValue());

        verifyNoMoreInteractions(carrier);

        // ignore outdated timeout
        service.speakerTimeout(alphaLogicalEndpoint, requestKey + "1");
        verifyNoMoreInteractions(carrier);

        service.speakerResponse(alphaLogicalEndpoint, requestKey + "2",
                new BfdSessionResponse(removeBfdSessionArgument.getValue(),
                                                       NoviBfdSession.Errors.NOVI_BFD_DISCRIMINATOR_NOT_FOUND_ERROR));
        verify(carrier).sessionRotateRequest(alphaLogicalEndpoint, false);
        verify(carrier).sessionCompleteNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void enableEnable() {
        createOperationalSession();

        // active
        when(carrier.sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class))).thenReturn(removeRequestKey);
        BfdProperties update = BfdProperties.builder()
                .interval(Duration.ofMillis(genericBfdProperties.getInterval().toMillis() + 100))
                .multiplier((short) (genericBfdProperties.getMultiplier() + 1))
                .build();
        service.enableUpdate(
                alphaLogicalEndpoint, alphaEndpoint.getPortNumber(), new BfdSessionData(alphaToBetaIslRef, update));

        verify(carrier).bfdKillNotification(alphaEndpoint);

        ArgumentCaptor<NoviBfdSession> speakerBfdRequestArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionDeleteRequest(speakerBfdRequestArgument.capture());
        NoviBfdSession speakerBfdSession = speakerBfdRequestArgument.getValue();

        verifyNoMoreInteractions(carrier);
        resetCarrier();

        // reset
        mockMissingBfdSession(alphaLogicalEndpoint);
        when(carrier.sendWorkerBfdSessionCreateRequest(any(NoviBfdSession.class))).thenReturn(setupRequestKey);

        BfdSessionResponse removeResponse = new BfdSessionResponse(speakerBfdSession, null);
        service.speakerResponse(alphaLogicalEndpoint, removeRequestKey, removeResponse);

        verify(carrier).sendWorkerBfdSessionCreateRequest(speakerBfdRequestArgument.capture());
        speakerBfdSession = speakerBfdRequestArgument.getValue();

        Assert.assertEquals(update.getInterval().toMillis(), speakerBfdSession.getIntervalMs());
        Assert.assertEquals(update.getMultiplier(), speakerBfdSession.getMultiplier());

        verify(carrier).sessionRotateRequest(alphaLogicalEndpoint, false);
        verifyNoMoreInteractions(carrier);

        resetCarrier();
        reset(bfdSessionRepository);

        // do_setup
        BfdSession dbView = BfdSession.builder()
                .switchId(alphaLogicalEndpoint.getDatapath())
                .port(alphaLogicalEndpoint.getPortNumber())
                .physicalPort(alphaEndpoint.getPortNumber())
                .interval(genericBfdProperties.getInterval())
                .multiplier(genericBfdProperties.getMultiplier())
                .build();
        when(bfdSessionRepository.findBySwitchIdAndPort(
                alphaLogicalEndpoint.getDatapath(), alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(dbView));
        BfdSessionResponse setupResponse = new BfdSessionResponse(speakerBfdSession, null);
        service.speakerResponse(alphaLogicalEndpoint, setupRequestKey, setupResponse);
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);
        verify(carrier).bfdUpNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);
        resetCarrier();

        Assert.assertEquals(update.getInterval(), dbView.getInterval());
        Assert.assertEquals(update.getMultiplier(), BfdProperties.normalizeMultiplier(dbView.getMultiplier()));

        // active
        // ensure we are reaction on link status update
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.DOWN);
        verify(carrier).bfdDownNotification(alphaEndpoint);
    }

    @Test
    public void enableForAllocatedDiscriminatorByNotCreatedSession() {
        BfdSession prehistory = makeBfdSessionBuilder(1)
                .interval(null).multiplier((short) 0)
                .build();
        mockBfdSessionLookup(prehistory);

        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);

        switchOnlineStatusMonitor.update(prehistory.getSwitchId(), true);

        when(carrier.sendWorkerBfdSessionCreateRequest(any(NoviBfdSession.class))).thenReturn(setupRequestKey);
        service.enableUpdate(
                Endpoint.of(prehistory.getSwitchId(), prehistory.getPort()),
                prehistory.getPhysicalPort(), new BfdSessionData(alphaToBetaIslRef, genericBfdProperties));
        verify(carrier).sendWorkerBfdSessionCreateRequest(any(NoviBfdSession.class));
        verifyNoMoreInteractions(carrier);
    }

    private NoviBfdSession createOperationalSession() {
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);

        doAnswer(invocation -> invocation.getArgument(0))
                .when(bfdSessionRepository).add(any());

        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);
        mockMissingBfdSession(alphaLogicalEndpoint);
        when(carrier.sendWorkerBfdSessionCreateRequest(any(NoviBfdSession.class))).thenReturn(setupRequestKey);
        service.enableUpdate(
                alphaLogicalEndpoint, alphaEndpoint.getPortNumber(),
                new BfdSessionData(alphaToBetaIslRef, genericBfdProperties));

        ArgumentCaptor<NoviBfdSession> speakerBfdSetupRequestArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionCreateRequest(speakerBfdSetupRequestArgument.capture());
        NoviBfdSession speakerBfdSession = speakerBfdSetupRequestArgument.getValue();

        verify(bfdSessionRepository).add(any(BfdSession.class));

        resetCarrier();
        reset(bfdSessionRepository);

        // speaker response
        BfdSessionResponse response = new BfdSessionResponse(speakerBfdSession, null);
        service.speakerResponse(alphaLogicalEndpoint, setupRequestKey, response);

        verifyNoMoreInteractions(carrier);
        resetCarrier();

        // port up
        endpointStatusMonitor.update(alphaLogicalEndpoint, LinkStatus.UP);
        verify(carrier).bfdUpNotification(alphaEndpoint);
        verifyNoMoreInteractions(carrier);

        resetCarrier();

        return speakerBfdSession;
    }

    private NoviBfdSession forceCleanupAfterInit(BfdSession initialBfdSession) {
        switchOnlineStatusMonitor.update(alphaLogicalEndpoint.getDatapath(), true);

        when(bfdSessionRepository.findBySwitchIdAndPort(alphaLogicalEndpoint.getDatapath(),
                                                        alphaLogicalEndpoint.getPortNumber()))
                .thenReturn(Optional.of(initialBfdSession));
        mockSwitchLookup(alphaSwitch);
        mockSwitchLookup(betaSwitch);

        when(carrier.sendWorkerBfdSessionDeleteRequest(any(NoviBfdSession.class))).thenReturn(removeRequestKey);

        service.enableUpdate(
                alphaLogicalEndpoint, alphaEndpoint.getPortNumber(),
                new BfdSessionData(alphaToBetaIslRef, genericBfdProperties));

        ArgumentCaptor<NoviBfdSession> setupBfdSessionArgument = ArgumentCaptor.forClass(NoviBfdSession.class);
        verify(carrier).sendWorkerBfdSessionDeleteRequest(setupBfdSessionArgument.capture());

        resetCarrier();
        reset(bfdSessionRepository);

        return setupBfdSessionArgument.getValue();
    }

    private BfdSession makeBfdSession(Integer discriminator) {
        return makeBfdSessionBuilder(discriminator).build();
    }

    private BfdSession.BfdSessionBuilder makeBfdSessionBuilder(Integer discriminator) {
        return BfdSession.builder()
                .switchId(alphaLogicalEndpoint.getDatapath())
                .port(alphaLogicalEndpoint.getPortNumber())
                .physicalPort(alphaEndpoint.getPortNumber())
                .ipAddress(alphaAddress)
                .remoteSwitchId(betaLogicalEndpoint.getDatapath())
                .remoteIpAddress(betaAddress)
                .discriminator(discriminator)
                .interval(genericBfdProperties.getInterval())
                .multiplier(genericBfdProperties.getMultiplier());
    }

    private void mockSwitchLookup(Switch sw) {
        when(switchRepository.findById(sw.getSwitchId())).thenReturn(Optional.ofNullable(sw));
    }

    private void mockMissingBfdSession(Endpoint endpoint) {
        when(bfdSessionRepository.findBySwitchIdAndPort(endpoint.getDatapath(), endpoint.getPortNumber()))
                .thenReturn(Optional.empty());
    }

    private void mockBfdSessionLookup(BfdSession session) {
        when(bfdSessionRepository.findBySwitchIdAndPort(session.getSwitchId(), session.getPort()))
                .thenReturn(Optional.of(session));
    }

    private InetSocketAddress getSocketAddress(String host, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private void resetCarrier() {
        reset(carrier);
        setupCarrier();
    }

    private void setupCarrier() {
        doAnswer(invocation -> {
            Endpoint logical = invocation.getArgument(0);
            boolean error = invocation.getArgument(1);
            service.rotate(logical, error);
            return null;
        }).when(carrier).sessionRotateRequest(any(Endpoint.class), any(Boolean.class));
    }
}
