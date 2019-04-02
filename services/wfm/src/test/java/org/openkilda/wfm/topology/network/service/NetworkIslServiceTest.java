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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionCallbackWithoutResult;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
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
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class NetworkIslServiceTest {
    private static EmbeddedNeo4jDatabase dbTestServer;

    @ClassRule
    public static TemporaryFolder fsData = new TemporaryFolder();

    private final Endpoint endpointAlpha1 = Endpoint.of(new SwitchId(1), 1);
    private final Endpoint endpointBeta2 = Endpoint.of(new SwitchId(2), 2);

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
    private FlowSegmentRepository flowSegmentRepository;
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
        when(repositoryFactory.createFlowSegmentRepository()).thenReturn(flowSegmentRepository);
        when(repositoryFactory.createFeatureTogglesRepository()).thenReturn(featureTogglesRepository);

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
        IslDataHolder islData = new IslDataHolder(1000, 50, 1000);
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
        Switch alphaSwitch = Switch.builder()
                .switchId(endpointAlpha1.getDatapath())
                .description("alpha-switch mock")
                .build();
        Switch betaSwitch = Switch.builder()
                .switchId(endpointBeta2.getDatapath())
                .description("beta-switch mock")
                .build();

        Isl islAlphaBeta = Isl.builder()
                .srcSwitch(alphaSwitch).srcPort(endpointAlpha1.getPortNumber())
                .destSwitch(betaSwitch).destPort(endpointBeta2.getPortNumber())
                .status(IslStatus.ACTIVE)
                .availableBandwidth(90)
                .maxBandwidth(100)
                .defaultMaxBandwidth(100)
                .build();
        Isl islBetaAlpha = islAlphaBeta.toBuilder()
                .srcSwitch(betaSwitch).srcPort(endpointBeta2.getPortNumber())
                .destSwitch(alphaSwitch).destPort(endpointAlpha1.getPortNumber())
                .build();

        when(switchRepository.findById(endpointAlpha1.getDatapath())).thenReturn(Optional.of(alphaSwitch));
        when(switchRepository.findById(endpointBeta2.getDatapath())).thenReturn(Optional.of(betaSwitch));

        when(islRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                           endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Optional.of(islAlphaBeta));
        when(islRepository.findByEndpoints(endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                                           endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber()))
                .thenReturn(Optional.of(islBetaAlpha));

        when(linkPropsRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                                 endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Collections.emptyList());
        when(linkPropsRepository.findByEndpoints(endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                                                 endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber()))
                .thenReturn(Collections.emptyList());

        when(flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                endpointBeta2.getDatapath(), endpointBeta2.getPortNumber())).thenReturn(10L);
        when(flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                endpointBeta2.getDatapath(), endpointBeta2.getPortNumber(),
                endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber())).thenReturn(10L);

        when(featureTogglesRepository.find()).thenReturn(Optional.empty());

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

    private void emulateEmptyPersistentDb() {
        when(islRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                           endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Optional.empty());
        when(linkPropsRepository.findByEndpoints(endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                                                 endpointBeta2.getDatapath(), endpointBeta2.getPortNumber()))
                .thenReturn(Collections.emptyList());
        when(flowSegmentRepository.getUsedBandwidthBetweenEndpoints(
                endpointAlpha1.getDatapath(), endpointAlpha1.getPortNumber(),
                endpointBeta2.getDatapath(), endpointBeta2.getPortNumber())).thenReturn(0L);
    }
}
