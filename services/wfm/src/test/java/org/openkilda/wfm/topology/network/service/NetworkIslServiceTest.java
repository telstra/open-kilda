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
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.EmbeddedNeo4jDatabase;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.IslReference;
import org.openkilda.wfm.topology.network.model.NetworkOptions;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class NetworkIslServiceTest {
    private static EmbeddedNeo4jDatabase dbTestServer;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    private final Endpoint endpointAlpha1 = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint endpointBeta2 = Endpoint.of(new SwitchId(2), 2);
    private final Map<SwitchId, Switch> allocatedSwitches = new HashMap<>();

    private final NetworkOptions options = NetworkOptions.builder()
            .islCostRaiseOnPhysicalDown(10000)
            .islCostWhenUnderMaintenance(15000)
            .build();

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
    private IslRepository islRepository;
    @Mock
    private LinkPropsRepository linkPropsRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private FeatureTogglesRepository featureTogglesRepository;

    private NetworkIslService service;

    @Before
    public void setUp() {
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);

        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createIslRepository()).thenReturn(islRepository);
        when(repositoryFactory.createLinkPropsRepository()).thenReturn(linkPropsRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);

        when(featureTogglesRepository.find()).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            TransactionCallbackWithoutResult tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(Mockito.any(TransactionCallbackWithoutResult.class));

        service = new NetworkIslService(carrier, persistenceManager, options);
    }

    @Test
    @Ignore("incomplete")
    public void initialUp() {
        dbTestServer = new EmbeddedNeo4jDatabase(fsData.getRoot());
        persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() { //NOSONAR
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return dbTestServer.getConnectionUri();
                                }

                                @Override
                                public String getLogin() {
                                    return "";
                                }

                                @Override
                                public String getPassword() {
                                    return "";
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }

                                @Override
                                public String getIndexesAuto() {
                                    return "update";
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                                                            + configurationType);
                        }
                    }
                });
        emulateEmptyPersistentDb();

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory()
                .createSwitchRepository();
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            switchRepository.createOrUpdate(Switch.builder()
                                                    .switchId(endpointAlpha1.getDatapath())
                                                    .description("alpha")
                                                    .build());
            switchRepository.createOrUpdate(Switch.builder()
                                                    .switchId(endpointBeta2.getDatapath())
                                                    .description("alpha")
                                                    .build());
        });

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        IslDataHolder islData = new IslDataHolder(1000, 1000, 1000);
        service = new NetworkIslService(carrier, persistenceManager, options);
        service.islUp(ref.getSource(), ref, islData);

        System.out.println(mockingDetails(carrier).printInvocations());
        System.out.println(mockingDetails(islRepository).printInvocations());
    }

    @Test
    @Ignore("become invalid due to change initialisation logic")
    public void initialMoveEvent() {
        emulateEmptyPersistentDb();

        IslReference ref = new IslReference(endpointAlpha1, endpointBeta2);
        service.islMove(ref.getSource(), ref);

        // System.out.println(mockingDetails(carrier).printInvocations());
        verify(carrier, times(2)).triggerReroute(any(RerouteAffectedFlows.class));

        // System.out.println(mockingDetails(islRepository).printInvocations());
        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(endpointAlpha1.getDatapath())
                                && link.getSrcPort() == endpointAlpha1.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(endpointBeta2.getDatapath())
                                && link.getDestPort() == endpointBeta2.getPortNumber()
                                && link.getActualStatus() == IslStatus.INACTIVE
                                && link.getStatus() == IslStatus.INACTIVE));
        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(endpointBeta2.getDatapath())
                                && link.getSrcPort() == endpointBeta2.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(endpointAlpha1.getDatapath())
                                && link.getDestPort() == endpointAlpha1.getPortNumber()
                                && link.getActualStatus() == IslStatus.INACTIVE
                                && link.getStatus() == IslStatus.INACTIVE));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void initializeFromHistoryDoNotReAllocateUsedBandwidth() {
        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .availableBandwidth(90)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .availableBandwidth(90)
                .build();

        mockPersistenceIsl(endpointAlpha1, endpointBeta2, islAlphaBeta);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, islBetaAlpha);
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);
        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 10L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 10L);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(this.endpointAlpha1.getDatapath())
                                && link.getSrcPort() == this.endpointAlpha1.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(this.endpointBeta2.getDatapath())
                                && link.getDestPort() == this.endpointBeta2.getPortNumber()
                                && link.getActualStatus() == IslStatus.ACTIVE
                                && link.getStatus() == IslStatus.ACTIVE
                                && link.getAvailableBandwidth() == 90));
        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(this.endpointBeta2.getDatapath())
                                && link.getSrcPort() == this.endpointBeta2.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(this.endpointAlpha1.getDatapath())
                                && link.getDestPort() == this.endpointAlpha1.getPortNumber()
                                && link.getActualStatus() == IslStatus.ACTIVE
                                && link.getStatus() == IslStatus.ACTIVE
                                && link.getAvailableBandwidth() == 90));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void initializeFromHistoryDoNotResetDefaultMaxBandwidth() {
        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .availableBandwidth(100)
                .defaultMaxBandwidth(200)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .availableBandwidth(100)
                .defaultMaxBandwidth(200)
                .build();

        mockPersistenceIsl(endpointAlpha1, endpointBeta2, islAlphaBeta);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, islBetaAlpha);
        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);
        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0L);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(this.endpointAlpha1.getDatapath())
                                && link.getSrcPort() == this.endpointAlpha1.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(this.endpointBeta2.getDatapath())
                                && link.getDestPort() == this.endpointBeta2.getPortNumber()
                                && link.getAvailableBandwidth() == 100
                                && link.getDefaultMaxBandwidth() == 200));
        verify(islRepository).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(this.endpointBeta2.getDatapath())
                                && link.getSrcPort() == this.endpointBeta2.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(this.endpointAlpha1.getDatapath())
                                && link.getDestPort() == this.endpointAlpha1.getPortNumber()
                                && link.getAvailableBandwidth() == 100
                                && link.getDefaultMaxBandwidth() == 200));

        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void changeIslCostAndLinkPropsCostOnPortDown() {
        // prepare data
        Isl islAlphaBeta = makeIsl(endpointAlpha1, endpointBeta2)
                .cost(10)
                .build();
        Isl islBetaAlpha = makeIsl(endpointBeta2, endpointAlpha1)
                .cost(20)
                .build();

        mockPersistenceIsl(endpointAlpha1, endpointBeta2, null);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, null);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                                 makeLinkProps(endpointAlpha1, endpointBeta2).cost(10).build());
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0L);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0L);

        // setup alpha -> beta half
        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islUp(endpointAlpha1, reference, new IslDataHolder(islAlphaBeta));

        // setup beta -> alpha half
        reset(islRepository);
        when(islRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                           endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Optional.of(islAlphaBeta.toBuilder().build()));
        when(islRepository.findByEndpoints(endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                                           endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber()))
                .thenReturn(Optional.of(islBetaAlpha.toBuilder().build()));
        service.islUp(endpointBeta2, reference, new IslDataHolder(islBetaAlpha));

        // mock/prepare persistent data
        reset(carrier);
        reset(islRepository);
        reset(linkPropsRepository);

        when(islRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                           endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Optional.of(islAlphaBeta));
        when(islRepository.findByEndpoints(endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                                           endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber()))
                .thenReturn(Optional.of(islBetaAlpha));

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2,
                                 makeLinkProps(endpointAlpha1, endpointBeta2).cost(10).build());

        // isl fail by PORT DOWN
        service.islDown(endpointAlpha1, reference, IslDownReason.PORT_DOWN);

        // ensure we have stored cost update
        verify(islRepository, atLeastOnce()).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(this.endpointAlpha1.getDatapath())
                                && link.getSrcPort() == this.endpointAlpha1.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(this.endpointBeta2.getDatapath())
                                && link.getDestPort() == this.endpointBeta2.getPortNumber()
                                && link.getCost() == options.getIslCostRaiseOnPhysicalDown() + 10));
        verify(islRepository, atLeastOnce()).createOrUpdate(argThat(
                link ->
                        link.getSrcSwitch().getSwitchId().equals(this.endpointBeta2.getDatapath())
                                && link.getSrcPort() == this.endpointBeta2.getPortNumber()
                                && link.getDestSwitch().getSwitchId().equals(this.endpointAlpha1.getDatapath())
                                && link.getDestPort() == this.endpointAlpha1.getPortNumber()
                                && link.getCost() == options.getIslCostRaiseOnPhysicalDown() + 20));

        verify(linkPropsRepository).findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                                    endpointBeta2.getDatapath(), endpointBeta2.getPortNumber());
        verify(linkPropsRepository).createOrUpdate(argThat(
                props ->
                        props.getSrcSwitchId() == endpointAlpha1.getDatapath()
                                && props.getSrcPort() == endpointAlpha1.getPortNumber()
                                && props.getDstSwitchId() == endpointBeta2.getDatapath()
                                && props.getDstPort() == endpointBeta2.getPortNumber()
                                && props.getCost() == options.getIslCostRaiseOnPhysicalDown() + 10));
        verify(linkPropsRepository).findByEndpoints(endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                                                    endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber());
        verifyNoMoreInteractions(linkPropsRepository);
    }

    @Test
    public void deleteWhenActive() {
        prepareAndPerformDelete(IslStatus.ACTIVE);

        // ISL can't be delete in ACTIVE state
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void deleteWhenInactive() {
        prepareAndPerformDelete(IslStatus.INACTIVE);
        verifyDelete();
    }

    @Test
    public void deleteWhenMoved() {
        prepareAndPerformDelete(IslStatus.MOVED);
        verifyDelete();
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
        mockPersistenceIsl(endpointAlpha1, endpointBeta2, islAlphaBeta);
        mockPersistenceIsl(endpointBeta2, endpointAlpha1, islBetaAlpha);

        mockPersistenceLinkProps(endpointAlpha1, endpointBeta2, null);
        mockPersistenceLinkProps(endpointBeta2, endpointAlpha1, null);

        mockPersistenceBandwidthAllocation(endpointAlpha1, endpointBeta2, 0);
        mockPersistenceBandwidthAllocation(endpointBeta2, endpointAlpha1, 0);

        IslReference reference = new IslReference(endpointAlpha1, endpointBeta2);
        service.islSetupFromHistory(endpointAlpha1, reference, islAlphaBeta);

        reset(carrier);

        // remove
        service.remove(reference);
    }

    private void verifyDelete() {
        verify(carrier).bfdDisableRequest(endpointAlpha1);
        verify(carrier).bfdDisableRequest(endpointBeta2);

        verifyNoMoreInteractions(carrier);
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
    }

    private void mockPersistenceLinkProps(Endpoint source, Endpoint dest, LinkProps props) {
        List<LinkProps> response = Collections.emptyList();
        if (props != null) {
            response = Collections.singletonList(props);
        }
        when(linkPropsRepository.findByEndpoints(source.getDatapath(), source.getPortNumber(),
                                                 dest.getDatapath(), dest.getPortNumber()))
                .thenReturn(response);
    }

    private void mockPersistenceBandwidthAllocation(Endpoint source, Endpoint dest, long allocation) {
        when(flowPathRepository.getUsedBandwidthBetweenEndpoints(source.getDatapath(), source.getPortNumber(),
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
            allocatedSwitches.put(datapath, entry);
            when(switchRepository.findById(datapath)).thenReturn(Optional.of(entry));
        }

        return entry;
    }
}
