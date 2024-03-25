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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.BfdSessionStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionCallbackWithoutResult;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.error.IslControllerNotFoundException;
import org.openkilda.wfm.topology.network.model.BfdStatusUpdate;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class NetworkIslServiceTest {

    private final ManualClock clock = new ManualClock();

    private final StubIslStorage islStorage = new StubIslStorage();
    private final Map<SwitchId, Switch> switchStorage = new HashMap<>();

    private final Endpoint endpointAlpha1 = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint endpointBeta2 = Endpoint.of(new SwitchId(2), 2);
    private final Map<SwitchId, Switch> allocatedSwitches = new HashMap<>();

    private final NetworkOptions options = NetworkOptions.builder()
            .dbRepeatMaxDurationSeconds(30)
            .discoveryTimeout(Duration.ofSeconds(3))
            .build();

    private final BfdProperties genericBfdProperties = BfdProperties.builder()
            .interval(Duration.ofMillis(350))
            .multiplier((short) 3)
            .build();

    @Mock
    private NetworkTopologyDashboardLogger dashboardLogger;

    @Mock
    private IIslCarrier carrier;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private RepositoryFactory repositoryFactory;

    @Mock
    private SwitchRepository switchRepository;
    @Mock
    private SwitchPropertiesRepository switchPropertiesRepository;
    @Mock
    private IslRepository islRepository;
    @Mock
    private LinkPropsRepository linkPropsRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private KildaFeatureTogglesRepository featureTogglesRepository;

    private NetworkIslService service;

    @BeforeEach
    public void setUp() {
        lenient().when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        lenient().when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        lenient().when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        lenient().when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        lenient().when(repositoryFactory.createLinkPropsRepository()).thenReturn(linkPropsRepository);
        lenient().when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        lenient().when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);
        lenient().when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);

        KildaFeatureToggles featureToggles = new KildaFeatureToggles(KildaFeatureToggles.DEFAULTS);
        featureToggles.setFlowsRerouteOnIslDiscoveryEnabled(true);
        lenient().when(featureTogglesRepository.getOrDefault()).thenReturn(featureToggles);

        lenient().when(transactionManager.getDefaultRetryPolicy())
                .thenReturn(RetryPolicy.builder().withMaxRetries(2));
        lenient().doAnswer(invocation -> {
            TransactionCallbackWithoutResult<?> tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));
        lenient()
                .doAnswer(invocation -> {
                    RetryPolicy<?> retryPolicy = invocation.getArgument(0);
                    TransactionCallbackWithoutResult<?> tr = invocation.getArgument(1);
                    Failsafe.with(retryPolicy)
                            .run(tr::doInTransaction);
                    return null;
                }).when(transactionManager)
                .doInTransaction(Mockito.any(RetryPolicy.class), Mockito.any(TransactionCallbackWithoutResult.class));

        NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder = mock(
                NetworkTopologyDashboardLogger.Builder.class);
        lenient().when(dashboardLoggerBuilder.build(any())).thenReturn(dashboardLogger);
        service = new NetworkIslService(carrier, persistenceManager, options, dashboardLoggerBuilder, clock);
    }

    private void setupIslStorageStub() {
        lenient().doAnswer(invocation -> {
            Endpoint ingress = Endpoint.of(invocation.getArgument(0), invocation.getArgument(1));
            Endpoint egress = Endpoint.of(invocation.getArgument(2), invocation.getArgument(3));
            return islStorage.lookup(ingress, egress);
        }).when(islRepository).findByEndpoints(any(SwitchId.class), anyInt(), any(SwitchId.class), anyInt());
        lenient().doAnswer(invocation -> {
            Isl payload = invocation.getArgument(0);
            islStorage.save(payload);
            return null;
        }).when(islRepository).add(any(Isl.class));
    }

    @Test
    @Disabled("incomplete")
    public void initialUp() {
        persistenceManager = InMemoryGraphPersistenceManager.newInstance();
        persistenceManager.install();

        emulateEmptyPersistentDb();

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory()
                .createSwitchRepository();
        Switch swA = Switch.builder()
                .switchId(endpointAlpha1.getDatapath())
                .description("alpha")
                .build();
        switchRepository.add(swA);
        switchPropertiesRepository.add(SwitchProperties.builder()
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                .switchObj(swA).build());
        Switch swB = Switch.builder()
                .switchId(endpointBeta2.getDatapath())
                .description("alpha")
                .build();
        switchRepository.add(swB);
        switchPropertiesRepository.add(SwitchProperties.builder()
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                .switchObj(swB).build());

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        IslDataHolder islData = new IslDataHolder(1000, 1000, 1000);
        service = new NetworkIslService(carrier, persistenceManager, options);
        service.islUp(ref.getSource(), ref, islData);

        System.out.println(mockingDetails(carrier).printInvocations());
        System.out.println(mockingDetails(islRepository).printInvocations());
    }

    @Test
    @Disabled("become invalid due to change initialisation logic")
    public void initialMoveEvent() {
        emulateEmptyPersistentDb();

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        service.islMove(ref.getSource(), ref);

        // System.out.println(mockingDetails(carrier).printInvocations());
        verify(carrier, times(2)).triggerReroute(any(RerouteAffectedFlows.class));

        // System.out.println(mockingDetails(islRepository).printInvocations());
        verify(islRepository).add(argThat(
                link ->
                        link.getSrcSwitchId().equals(endpointAlpha1.getDatapath())
                                && link.getSrcPort() == endpointAlpha1.getPortNumber()
                                && link.getDestSwitchId().equals(endpointBeta2.getDatapath())
                                && link.getDestPort() == endpointBeta2.getPortNumber()
                                && link.getActualStatus() == IslStatus.INACTIVE
                                && link.getStatus() == IslStatus.INACTIVE));
        verify(islRepository).add(argThat(
                link ->
                        link.getSrcSwitchId().equals(endpointBeta2.getDatapath())
                                && link.getSrcPort() == endpointBeta2.getPortNumber()
                                && link.getDestSwitchId().equals(endpointAlpha1.getDatapath())
                                && link.getDestPort() == endpointAlpha1.getPortNumber()
                                && link.getActualStatus() == IslStatus.INACTIVE
                                && link.getStatus() == IslStatus.INACTIVE));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void setIslUnstableTimeOnPortDown() {
        setupIslStorageStub();
        IslReference reference = prepareActiveIsl();

        Instant updateTime = clock.adjust(Duration.ofSeconds(1));

        // isl fail by PORT DOWN
        service.islDown(endpointAlpha1, reference, IslDownReason.PORT_DOWN);

        // ensure we have marked ISL as unstable
        Isl forward = lookupIsl(reference.getSource(), reference.getDest());
        assertEquals(updateTime, forward.getTimeUnstable());

        Isl reverse = lookupIsl(reference.getDest(), reference.getSource());
        assertEquals(updateTime, reverse.getTimeUnstable());
    }

    @Test
    public void deleteWhenActive() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.ACTIVE);

        // ISL can't be delete in ACTIVE state
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void deleteWhenInactive() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.INACTIVE);
        verifyDelete();
    }

    @Test
    public void deleteWhenMoved() {
        setupIslStorageStub();
        prepareAndPerformDelete(IslStatus.MOVED);
        verifyDelete();
    }

    @Test
    public void repeatOnTransientDbErrors() {
        mockPersistenceIsl(endpointAlpha1, endpointBeta2, null);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, null);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0);

        /*TODO: reimplement with new datamodel
           doThrow(new PersistenceException("force createOrUpdate to fail"))
                .when(islRepository)
                .add(argThat(
                        link -> endpointAlpha1.getDatapath().equals(link.getSrcSwitchId())
                                && Objects.equals(endpointAlpha1.getPortNumber(), link.getSrcPort())));*/

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(100, 1, 100));

        assertEquals(new SwitchId(1), endpointAlpha1.getDatapath());
        assertEquals(1, endpointAlpha1.getPortNumber());
    }

    private void prepareAndPerformDelete(IslStatus initialStatus) {
        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .actualStatus(initialStatus)
                .status(initialStatus)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .actualStatus(initialStatus)
                .status(initialStatus)
                .build();

        // prepare
        islStorage.save(islAlphaBeta);
        islStorage.save(islBetaAlpha);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        reset(carrier);

        // remove
        service.remove(reference);

        if (initialStatus == IslStatus.INACTIVE || initialStatus == IslStatus.MOVED) {
            service.islRulesDeleted(reference, endpointAlpha1);
            service.islRulesDeleted(reference, endpointBeta2);
            verify(carrier).islRulesDelete(reference, endpointAlpha1);
            verify(carrier).islRulesDelete(reference, endpointBeta2);
        }
    }

    private void verifyDelete() {
        verify(carrier).bfdDisableRequest(endpointAlpha1);
        verify(carrier).bfdDisableRequest(endpointBeta2);

        verify(carrier).auxiliaryPollModeUpdateRequest(endpointAlpha1, false);
        verify(carrier).auxiliaryPollModeUpdateRequest(endpointBeta2, false);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        verify(carrier).islRemovedNotification(endpointAlpha1, reference);
        verify(carrier).islRemovedNotification(endpointBeta2, reference);
        verify(carrier).islChangedNotifyFlowMonitor(reference, true);

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void considerLinkPropsDataOnHistory() {
        setupIslStorageStub();

        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .maxBandwidth(100L)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .maxBandwidth(100L)
                .build();

        islStorage.save(islAlphaBeta);
        islStorage.save(islBetaAlpha);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                makeLinkProps(endpointAlpha1, endpointBeta2)
                        .maxBandwidth(50L)
                        .build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0L);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        // need to change at least one ISL field to force DB sync
        service.islUp(endpointAlpha1, reference, new IslDataHolder(200L, 200L, 200L));

        verifyIslBandwidthUpdate(50L, 100L);
    }

    @Test
    public void considerLinkPropsDataOnCreate() {
        setupIslStorageStub();

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                makeLinkProps(endpointAlpha1, endpointBeta2)
                        .maxBandwidth(50L)
                        .build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0L);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(200L, 200L, 200L));

        service.islUp(endpointBeta2, reference, new IslDataHolder(200L, 200L, 200L));

        verifyIslBandwidthUpdate(50L, 200L);
    }

    @Test
    public void considerLinkPropsDataOnRediscovery() {
        setupIslStorageStub();

        final Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .defaultMaxBandwidth(300L)
                .availableBandwidth(300L)
                .build();
        final Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .defaultMaxBandwidth(300L)
                .availableBandwidth(300L)
                .build();

        // initial discovery
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(300L, 300L, 300L));
        service.islUp(endpointBeta2, reference, new IslDataHolder(300L, 300L, 300L));

        verifyIslBandwidthUpdate(300L, 300L);

        // fail
        service.islDown(endpointAlpha1, reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(endpointBeta2, reference, IslDownReason.POLL_TIMEOUT);

        reset(linkPropsRepository);

        // rediscovery
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                makeLinkProps(endpointAlpha1, endpointBeta2)
                        .maxBandwidth(50L)
                        .build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        service.islUp(endpointAlpha1, reference, new IslDataHolder(300L, 300L, 300L));
        service.islUp(endpointBeta2, reference, new IslDataHolder(300L, 300L, 300L));

        verifyIslBandwidthUpdate(50L, 300L);
    }

    @Test
    public void considerRoundTripDiscovery() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();

        // round trip discovery (one endpoint enough)
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), IslStatus.ACTIVE));

        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(reference.getDest(), reference, IslDownReason.POLL_TIMEOUT);

        verify(dashboardLogger, times(0)).onIslDown(eq(reference), any());

        // round trip fail/expire
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), IslStatus.INACTIVE));
        verify(dashboardLogger).onIslDown(eq(reference), any());
    }

    @Test
    public void considerRecoveryAfterRoundTrip() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), IslStatus.ACTIVE));

        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(reference.getDest(), reference, IslDownReason.POLL_TIMEOUT);
        // still up because of active round trip discovery
        verify(dashboardLogger, times(0)).onIslDown(eq(reference), any());

        // restore one-way discovery status
        service.islUp(reference.getSource(), reference, new IslDataHolder(200L, 200L, 200L));
        service.islUp(reference.getDest(), reference, new IslDataHolder(200L, 200L, 200L));

        // remove round trip discovery
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), IslStatus.INACTIVE));

        // must not fail, because of successful discovery notifications
        verify(dashboardLogger, times(0)).onIslDown(eq(reference), any());
    }

    @Test
    public void movedOverrideRoundTripState() {
        setupIslStorageStub();

        final IslReference reference = prepareActiveIsl();

        // inject round-trip status
        service.roundTripStatusNotification(
                reference, new RoundTripStatus(reference.getDest(), IslStatus.ACTIVE));

        Optional<Isl> forward = islStorage.lookup(reference.getSource(), reference.getDest());
        assertTrue(forward.isPresent());
        assertNotEquals(IslStatus.ACTIVE, forward.get().getRoundTripStatus());
        assertEquals(IslStatus.ACTIVE, forward.get().getStatus());
        Optional<Isl> reverse = islStorage.lookup(reference.getDest(), reference.getSource());
        assertTrue(reverse.isPresent());
        assertEquals(IslStatus.ACTIVE, reverse.get().getRoundTripStatus());
        assertEquals(IslStatus.ACTIVE, reverse.get().getStatus());

        service.islMove(reference.getSource(), reference);

        verify(dashboardLogger).onIslMoved(eq(reference), any());
        forward = islStorage.lookup(reference.getSource(), reference.getDest());
        assertTrue(forward.isPresent());
        assertEquals(IslStatus.MOVED, forward.get().getActualStatus());
        assertEquals(IslStatus.MOVED, forward.get().getStatus());
        reverse = islStorage.lookup(reference.getDest(), reference.getSource());
        assertTrue(reverse.isPresent());
        assertNotEquals(IslStatus.MOVED, reverse.get().getActualStatus());
        assertEquals(IslStatus.MOVED, reverse.get().getStatus());

        verify(carrier).islStatusUpdateNotification(any(IslStatusUpdateNotification.class));
        verify(carrier).triggerReroute(any(RerouteAffectedFlows.class));
    }

    @Test
    public void considerBfdEventOnlyWhenBfdEnabledForBothEndpoints() {
        setupIslStorageStub();

        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .bfdInterval(genericBfdProperties.getInterval())
                .bfdMultiplier(genericBfdProperties.getMultiplier())
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .bfdInterval(genericBfdProperties.getInterval())
                .bfdMultiplier(genericBfdProperties.getMultiplier())
                .build();

        islStorage.save(islAlphaBeta);
        islStorage.save(islBetaAlpha);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);

        // system start
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        // BFD setup requests
        verify(carrier).bfdPropertiesApplyRequest(eq(reference.getSource()), eq(reference), eq(genericBfdProperties));
        verify(carrier).bfdPropertiesApplyRequest(eq(reference.getDest()), eq(reference), eq(genericBfdProperties));

        reset(dashboardLogger);

        // one BFD session is removed
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);

        // one BFD session is reinstalled
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.UP);
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);

        // second BFD session is reinstalled
        service.bfdStatusUpdate(endpointBeta2, reference, BfdStatusUpdate.UP);
        verify(carrier).auxiliaryPollModeUpdateRequest(eq(reference.getSource()), eq(true));
        verify(carrier).auxiliaryPollModeUpdateRequest(eq(reference.getDest()), eq(true));
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.UP);
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());

        verify(dashboardLogger, never()).onIslDown(eq(reference), any());

        // only now BFD events must be able to control ISL state
        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);
        verify(dashboardLogger, never()).onIslDown(eq(reference), any());  // opposite session still UP
        service.bfdStatusUpdate(endpointBeta2, reference, BfdStatusUpdate.DOWN);

        verify(dashboardLogger).onIslDown(eq(reference), any());  // both BFD session are down
        reset(dashboardLogger);

        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.UP);
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verify(dashboardLogger).onIslUp(eq(reference), any());  // one BFD session enough to raise ISL UP
        verifyNoMoreInteractions(dashboardLogger);
        reset(dashboardLogger);

        service.bfdStatusUpdate(endpointAlpha1, reference, BfdStatusUpdate.DOWN);
        verify(dashboardLogger).onIslDown(eq(reference), any());  // both BFD session are down (again)
    }

    @Test
    public void bfdUpOverriderPollDown() {
        setupIslStorageStub();

        IslReference reference = prepareBfdEnabledIsl();

        reset(dashboardLogger);
        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        verifyNoMoreInteractions(dashboardLogger);

        service.islUp(reference.getSource(), reference, new IslDataHolder(100, 100, 100));
        verifyNoMoreInteractions(dashboardLogger);

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.DOWN);
        service.bfdStatusUpdate(reference.getDest(), reference, BfdStatusUpdate.DOWN);

        // from poll point of view ISL is UP but BFD must force status to DOWN
        verify(dashboardLogger).onIslDown(eq(reference), any());
    }

    @Test
    public void portDownOverriderBfdUp() {
        setupIslStorageStub();

        IslReference reference = prepareBfdEnabledIsl();

        reset(dashboardLogger);
        service.islDown(reference.getSource(), reference, IslDownReason.PORT_DOWN);
        verify(dashboardLogger).onIslDown(eq(reference), any());
    }

    @Test
    public void resetBfdFailOnStart() {
        testBfdStatusReset(BfdSessionStatus.FAIL);
    }

    @Test
    public void resetBfdUpOnStart() {
        testBfdStatusReset(BfdSessionStatus.UP);
    }

    @Test
    public void continuousReplugVsRoundTripAlive() {
        setupIslStorageStub();

        IslReference referenceAlpha = prepareActiveIsl();
        IslReference referenceBeta = new IslReference(
                Endpoint.of(referenceAlpha.getSource().getDatapath(), referenceAlpha.getSource().getPortNumber() + 1),
                referenceAlpha.getDest());

        Instant lastSeen = clock.instant();
        service.roundTripStatusNotification(
                referenceAlpha, new RoundTripStatus(referenceAlpha.getSource(), IslStatus.ACTIVE));

        IslDataHolder alphaSource = new IslDataHolder(
                lookupIsl(referenceAlpha.getSource(), referenceAlpha.getDest()));
        IslDataHolder alphaDest = new IslDataHolder(
                lookupIsl(referenceAlpha.getDest(), referenceAlpha.getSource()));

        IslDataHolder betaSource = new IslDataHolder(
                makeIsl(referenceBeta.getSource(), referenceBeta.getDest()).build());
        IslDataHolder betaDest = new IslDataHolder(
                makeIsl(referenceBeta.getDest(), referenceBeta.getSource()).build());

        IslStatusUpdateNotification alphaNotification = new IslStatusUpdateNotification(
                referenceAlpha.getSource().getDatapath(), referenceAlpha.getSource().getPortNumber(),
                referenceAlpha.getDest().getDatapath(), referenceAlpha.getDest().getPortNumber(),
                IslStatus.MOVED);
        IslStatusUpdateNotification betaNotification = new IslStatusUpdateNotification(
                referenceBeta.getSource().getDatapath(), referenceBeta.getSource().getPortNumber(),
                referenceBeta.getDest().getDatapath(), referenceBeta.getDest().getPortNumber(),
                IslStatus.MOVED);
        for (int i = 0; i < 100; i++) {
            // alpha -> beta
            service.islMove(referenceAlpha.getSource(), referenceAlpha);
            service.islUp(referenceBeta.getSource(), referenceBeta, betaSource);
            service.islUp(referenceBeta.getDest(), referenceBeta, betaDest);
            service.islRulesInstalled(referenceBeta, referenceBeta.getSource());
            service.islRulesInstalled(referenceBeta, referenceBeta.getDest());
            service.roundTripStatusNotification(
                    referenceBeta, new RoundTripStatus(referenceBeta.getSource(), IslStatus.ACTIVE));

            verifyStatus(referenceAlpha, IslStatus.MOVED);
            verifyStatus(referenceBeta, IslStatus.ACTIVE);

            verify(carrier, times(i + 1)).islStatusUpdateNotification(eq(alphaNotification));
            verify(carrier, times(i + 1)).triggerReroute(argThat(
                    entry -> entry instanceof RerouteAffectedFlows && Objects.equals(
                            new PathNode(
                                    referenceAlpha.getSource().getDatapath(),
                                    referenceAlpha.getSource().getPortNumber(), 0),
                            entry.getPathNode())));
            verify(carrier, times(i + 1)).triggerReroute(argThat(
                    entry -> entry instanceof RerouteInactiveFlows && Objects.equals(
                            new PathNode(
                                    referenceBeta.getSource().getDatapath(),
                                    referenceBeta.getSource().getPortNumber(), 0),
                            entry.getPathNode())));

            // beta -> alpha
            service.islMove(referenceBeta.getSource(), referenceBeta);
            service.islUp(referenceAlpha.getSource(), referenceAlpha, alphaSource);
            service.islUp(referenceAlpha.getDest(), referenceAlpha, alphaDest);
            service.islRulesInstalled(referenceAlpha, referenceAlpha.getSource());
            service.islRulesInstalled(referenceAlpha, referenceAlpha.getDest());
            service.roundTripStatusNotification(
                    referenceAlpha, new RoundTripStatus(referenceAlpha.getSource(), IslStatus.ACTIVE));

            verifyStatus(referenceAlpha, IslStatus.ACTIVE);
            verifyStatus(referenceBeta, IslStatus.MOVED);
            verify(carrier, times(i + 1)).islStatusUpdateNotification(eq(betaNotification));
            verify(carrier, times(i + 1)).triggerReroute(argThat(
                    entry -> entry instanceof RerouteAffectedFlows && Objects.equals(
                            new PathNode(
                                    referenceBeta.getSource().getDatapath(),
                                    referenceBeta.getSource().getPortNumber(), 0),
                            entry.getPathNode())));
            verify(carrier, times(i + 1)).triggerReroute(argThat(
                    entry -> entry instanceof RerouteInactiveFlows && Objects.equals(
                            new PathNode(
                                    referenceAlpha.getSource().getDatapath(),
                                    referenceAlpha.getSource().getPortNumber(), 0),
                            entry.getPathNode())));
        }
    }

    private void testBfdStatusReset(BfdSessionStatus initialStatus) {
        setupIslStorageStub();

        final Instant start = clock.adjust(Duration.ofSeconds(1));
        Isl alphaToBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .bfdSessionStatus(initialStatus)
                .build();
        islStorage.save(alphaToBeta);
        islStorage.save(
                makeIsl(endpointBeta2, endpointAlpha1)
                        .bfdSessionStatus(initialStatus)
                        .build());

        clock.adjust(Duration.ofSeconds(1));

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(reference.getSource(), reference, alphaToBeta);

        Optional<Isl> potential = islStorage.lookup(reference.getSource(), reference.getDest());

        assertTrue(potential.isPresent());
        Isl link = potential.get();
        assertNull(link.getBfdSessionStatus());

        potential = islStorage.lookup(reference.getDest(), reference.getSource());
        assertTrue(potential.isPresent());
        link = potential.get();
        assertNull(link.getBfdSessionStatus());

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.UP);
        verifyBfdStatus(reference, BfdSessionStatus.UP, null);

        service.bfdStatusUpdate(reference.getDest(), reference, BfdStatusUpdate.UP);
        verifyBfdStatus(reference, BfdSessionStatus.UP, BfdSessionStatus.UP);
    }

    @Test
    public void ignoreBfdEventsDuringBfdUpdate() {
        setupIslStorageStub();
        IslReference reference = prepareBfdEnabledIsl();

        reset(dashboardLogger);
        service.bfdPropertiesUpdate(reference);
        verifyNoInteractions(dashboardLogger);

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.DOWN);
        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.KILL);
        verifyNoInteractions(dashboardLogger);

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.DOWN);
        verifyNoInteractions(dashboardLogger);

        service.bfdStatusUpdate(reference.getDest(), reference, BfdStatusUpdate.DOWN);
        verify(dashboardLogger).onIslDown(eq(reference), any());
        verifyNoMoreInteractions(dashboardLogger);
    }

    @Test
    public void pollDiscoveryResetsPortDownStatus() {
        IslReference reference = preparePortDownStatusReset();

        verifyNoInteractions(dashboardLogger); // only destination endpoint status is cleaned

        service.islUp(reference.getSource(), reference, new IslDataHolder(100, 100, 100));
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verifyNoInteractions(dashboardLogger); // only destination endpoint status is cleaned

        service.islUp(reference.getDest(), reference, new IslDataHolder(100, 100, 100));
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verify(dashboardLogger).onIslUp(eq(reference), any());
        verifyNoMoreInteractions(dashboardLogger);
    }

    @Test
    public void roundTripDiscoveryOnSourceResetPortDownStatus() {
        IslReference reference = preparePortDownStatusReset();

        service.roundTripStatusNotification(
                reference, new RoundTripStatus(reference.getSource(), IslStatus.ACTIVE));
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verifyNoMoreInteractions(dashboardLogger);

        service.roundTripStatusNotification(
                reference, new RoundTripStatus(reference.getSource(), IslStatus.ACTIVE));
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verify(dashboardLogger).onIslUp(eq(reference), any());
        verifyNoMoreInteractions(dashboardLogger);
    }

    @Test
    public void roundTripDiscoveryOnDestResetPortDownStatus() {
        IslReference reference = preparePortDownStatusReset();

        service.roundTripStatusNotification(
                reference, new RoundTripStatus(reference.getDest(), IslStatus.ACTIVE));
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verifyNoMoreInteractions(dashboardLogger);

        service.roundTripStatusNotification(
                reference, new RoundTripStatus(reference.getDest(), IslStatus.ACTIVE));
        service.islRulesInstalled(reference, reference.getSource());
        service.islRulesInstalled(reference, reference.getDest());
        verify(dashboardLogger).onIslUp(eq(reference), any());
        verifyNoMoreInteractions(dashboardLogger);
    }

    @Test
    public void resurrectAfterRemoval() {
        IslReference reference = prepareResurrection();
        service.islUp(reference.getSource(), reference, new IslDataHolder(1000, 1000, 1000));
        testResurrection(reference, true);
    }

    @Test
    public void noResurrectOnPollFail() {
        IslReference reference = prepareResurrection();
        service.islDown(reference.getSource(), reference, IslDownReason.POLL_TIMEOUT);
        testResurrection(reference, false);
    }

    @Test
    public void resurrectOnRoundTripDiscovery() {
        IslReference reference = prepareResurrection();
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), IslStatus.ACTIVE));
        testResurrection(reference, true);
    }

    @Test
    public void noResurrectOnRoundTripFail() {
        IslReference reference = prepareResurrection();
        service.roundTripStatusNotification(reference, new RoundTripStatus(reference.getSource(), IslStatus.INACTIVE));
        testResurrection(reference, false);
    }

    @Test
    public void noResurrectOnBfdUp() {
        IslReference reference = prepareResurrection();
        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.UP);
        testResurrection(reference, false);
    }

    @Test
    public void noResurrectOnBfdDown() {
        IslReference reference = prepareResurrection();
        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.DOWN);
        testResurrection(reference, false);
    }

    @Test
    public void noResurrectOnBfdFail() {
        IslReference reference = prepareResurrection();
        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.FAIL);
        testResurrection(reference, false);
    }

    @Test
    public void noResurrectOnBfdKill() {
        IslReference reference = prepareResurrection();
        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.KILL);
        testResurrection(reference, false);
    }

    private IslReference prepareResurrection() {
        setupIslStorageStub();

        IslReference reference = prepareActiveIsl();

        final Endpoint alphaEnd = reference.getSource();
        final Endpoint zetaEnd = reference.getDest();

        service.islDown(alphaEnd, reference, IslDownReason.POLL_TIMEOUT);
        service.islDown(zetaEnd, reference, IslDownReason.POLL_TIMEOUT);

        reset(carrier);
        service.remove(reference);

        verify(carrier).islRulesDelete(eq(reference), eq(alphaEnd));
        verify(carrier).islRulesDelete(eq(reference), eq(zetaEnd));
        verify(carrier).bfdDisableRequest(eq(alphaEnd));
        verify(carrier).bfdDisableRequest(eq(zetaEnd));
        verify(carrier).auxiliaryPollModeUpdateRequest(alphaEnd, false);
        verify(carrier).auxiliaryPollModeUpdateRequest(zetaEnd, false);
        verifyNoMoreInteractions(carrier);
        assertTrue(islStorage.lookup(alphaEnd, zetaEnd).isPresent());

        return reference;
    }

    private void testResurrection(IslReference reference, boolean shouldResurrect) {
        final Endpoint alphaEnd = reference.getSource();
        final Endpoint zetaEnd = reference.getDest();

        reset(carrier);
        service.islRulesDeleted(reference, alphaEnd);
        service.islRulesDeleted(reference, zetaEnd);

        if (!shouldResurrect) {
            verify(carrier).islRemovedNotification(eq(alphaEnd), eq(reference));
            verify(carrier).islRemovedNotification(eq(zetaEnd), eq(reference));
            verify(carrier).islChangedNotifyFlowMonitor(any(IslReference.class), eq(true));
        }
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void noIslCreationOnIslDown() {
        setupIslStorageStub();

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        for (IslDownReason reason : IslDownReason.values()) {
            try {
                service.islDown(reference.getSource(), reference, reason);
                fail("No expected exception IslControllerNotFoundException");
            } catch (IslControllerNotFoundException e) {
                // expected
            }
        }
    }

    private IslReference preparePortDownStatusReset() {
        setupIslStorageStub();
        IslReference reference = prepareActiveIsl();

        service.islDown(reference.getSource(), reference, IslDownReason.PORT_DOWN);
        verify(dashboardLogger).onIslDown(eq(reference), any());
        reset(dashboardLogger);

        return reference;
    }

    private IslReference prepareBfdEnabledIsl() {
        IslReference reference = prepareActiveIsl();

        service.bfdStatusUpdate(reference.getSource(), reference, BfdStatusUpdate.UP);
        service.bfdStatusUpdate(reference.getDest(), reference, BfdStatusUpdate.UP);

        return reference;
    }

    private IslReference prepareActiveIsl() {
        // prepare data
        final Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2).build();
        final Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1).build();

        // setup alpha -> beta half
        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(islAlphaBeta));
        verifyNoMoreInteractions(dashboardLogger);

        // setup beta -> alpha half
        service.islUp(endpointBeta2, reference, new IslDataHolder(islBetaAlpha));

        service.islRulesInstalled(reference, endpointAlpha1);
        service.islRulesInstalled(reference, endpointBeta2);

        verify(dashboardLogger).onIslUp(eq(reference), any());
        reset(dashboardLogger);
        reset(carrier);

        return reference;
    }

    private void verifyBfdStatus(IslReference reference, BfdSessionStatus leftToRight, BfdSessionStatus rightToLeft) {
        Optional<Isl> potential = islStorage.lookup(reference.getSource(), reference.getDest());
        assertTrue(potential.isPresent());
        verifyBfdStatus(potential.get(), leftToRight);

        potential = islStorage.lookup(reference.getDest(), reference.getSource());
        assertTrue(potential.isPresent());
        verifyBfdStatus(potential.get(), rightToLeft);
    }

    private void verifyBfdStatus(Isl link, BfdSessionStatus status) {
        if (status != null) {
            assertEquals(status, link.getBfdSessionStatus());
        } else {
            assertNull(link.getBfdSessionStatus());
        }
    }

    private void verifyIslBandwidthUpdate(long expectedForward, long expectedReverse) {
        Isl forward = lookupIsl(endpointAlpha1, endpointBeta2);
        assertEquals(expectedForward, forward.getMaxBandwidth());
        assertEquals(expectedForward, forward.getAvailableBandwidth());

        Isl reverse = lookupIsl(endpointBeta2, endpointAlpha1);
        assertEquals(expectedReverse, reverse.getMaxBandwidth());
        assertEquals(expectedReverse, reverse.getAvailableBandwidth());
    }

    private void verifyStatus(IslReference reference, IslStatus expectedStatus) {
        verifyStatus(lookupIsl(reference.getSource(), reference.getDest()), expectedStatus);
        verifyStatus(lookupIsl(reference.getDest(), reference.getSource()), expectedStatus);
    }

    private void verifyStatus(Isl persistedIsl, IslStatus expectedStatus) {
        assertEquals(expectedStatus, persistedIsl.getStatus());
    }

    private void emulateEmptyPersistentDb() {
        mockPersistenceIsl(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
    }

    private void mockPersistenceIsl(Endpoint source, Endpoint dest, Isl link) {
        when(islRepository.findByEndpoints(source.getDatapath(), source.getPortNumber(),
                dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(Optional.ofNullable(link));

        lenient().doAnswer(invocation -> invocation.getArgument(0)).when(islRepository).add(any(Isl.class));
    }

    private void mockPersistenceLinkProps(Endpoint source, Endpoint dest, LinkProps props) {
        List<LinkProps> response = Collections.emptyList();
        if (props != null) {
            response = Collections.singletonList(props);
        }
        lenient().when(linkPropsRepository.findByEndpoints(source.getDatapath(), source.getPortNumber(),
                        dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(response);
    }

    private void mockPersistenceBandwidthAllocation(Endpoint source, Endpoint dest, long allocation) {
        lenient().when(flowPathRepository.getUsedBandwidthBetweenEndpoints(source.getDatapath(), source.getPortNumber(),
                        dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(allocation);
    }

    private Isl.IslBuilder makeIsl(Endpoint source, Endpoint dest) {
        Switch sourceSwitch = lookupSwitchCreateMockIfAbsent(source.getDatapath());
        Switch destSwitch = lookupSwitchCreateMockIfAbsent(dest.getDatapath());
        return Isl.builder()
                .srcSwitch(sourceSwitch).srcPort(source.getPortNumber())
                .destSwitch(destSwitch).destPort(dest.getPortNumber())
                .status(IslStatus.ACTIVE)
                .actualStatus(IslStatus.ACTIVE)
                .latency(10)
                .speed(100)
                .availableBandwidth(100)
                .maxBandwidth(100)
                .defaultMaxBandwidth(100);
    }

    private LinkProps.LinkPropsBuilder makeLinkProps(Endpoint source, Endpoint dest) {
        return LinkProps.builder()
                .srcSwitchId(source.getDatapath()).srcPort(source.getPortNumber())
                .dstSwitchId(dest.getDatapath()).dstPort(dest.getPortNumber());
    }

    private Switch lookupSwitchCreateMockIfAbsent(SwitchId datapath) {
        Switch entry = allocatedSwitches.get(datapath);
        if (entry == null) {
            entry = Switch.builder()
                    .switchId(datapath)
                    .status(SwitchStatus.ACTIVE)
                    .description("autogenerated switch mock")
                    .build();
            SwitchProperties switchProperties = SwitchProperties.builder()
                    .switchObj(entry)
                    .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                    .build();
            allocatedSwitches.put(datapath, entry);

            lenient().when(switchRepository.findById(datapath)).thenReturn(Optional.of(entry));
            lenient().when(switchPropertiesRepository.findBySwitchId(datapath))
                    .thenReturn(Optional.of(switchProperties));
        }

        return entry;
    }

    private Isl lookupIsl(Endpoint ingress, Endpoint egress) {
        return islStorage.lookup(ingress, egress)
                .orElseThrow(() -> new AssertionError(
                        String.format(
                                "The ISL object between %s and %s endpoints is missing",
                                ingress, egress)));
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    private static class StubIslKey {
        private final Endpoint ingress;
        private final Endpoint egress;
    }

    private static class StubIslStorage {
        private final Map<StubIslKey, Isl> storage = new HashMap<>();

        public Optional<Isl> lookup(Isl reference) {
            return lookup(makeIngressEndpoint(reference), makeEgressEndpoint(reference));
        }

        public Optional<Isl> lookup(Endpoint ingress, Endpoint egress) {
            StubIslKey key = new StubIslKey(ingress, egress);
            return Optional.ofNullable(storage.get(key));
        }

        public void save(Isl payload) {
            StubIslKey key = new StubIslKey(makeIngressEndpoint(payload), makeEgressEndpoint(payload));
            storage.put(key, payload);
        }

        private static Endpoint makeIngressEndpoint(Isl entity) {
            return Endpoint.of(entity.getSrcSwitchId(), entity.getSrcPort());
        }

        private static Endpoint makeEgressEndpoint(Isl entity) {
            return Endpoint.of(entity.getDestSwitchId(), entity.getDestPort());
        }
    }
}
