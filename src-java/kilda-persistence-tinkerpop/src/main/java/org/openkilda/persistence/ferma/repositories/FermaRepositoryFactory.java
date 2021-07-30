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

package org.openkilda.persistence.ferma.repositories;

import org.openkilda.model.IslConfig;
import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.repositories.ApplicationRepository;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.ExclusionIdRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SpeakerRepository;
import org.openkilda.persistence.repositories.SwitchConnectRepository;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.PortEventRepository;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionManagerFactory;

import com.google.common.annotations.VisibleForTesting;

import java.time.Duration;

/**
 * Ferma (Tinkerpop) implementation of {@link RepositoryFactory}.
 */
public class FermaRepositoryFactory implements RepositoryFactory {
    protected final FramedGraphFactory<?> graphFactory;
    protected final TransactionManagerFactory transactionManagerFactory;
    protected final IslConfig islConfig;

    public FermaRepositoryFactory(
            FramedGraphFactory<?> graphFactory, TransactionManagerFactory transactionManagerFactory,
            NetworkConfig networkConfig) {
        this.graphFactory = graphFactory;
        this.transactionManagerFactory = transactionManagerFactory;
        this.islConfig = IslConfig.builder()
                .unstableIslTimeout(Duration.ofSeconds(networkConfig.getIslUnstableTimeoutSec()))
                .build();
    }

    @VisibleForTesting
    public FramedGraphFactory<?> getGraphFactory() {
        return graphFactory;
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return new FermaFlowCookieRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return new FermaFlowMeterRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowPathRepository createFlowPathRepository() {
        return new FermaFlowPathRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new FermaFlowRepository(
                graphFactory, createFlowPathRepository(), transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public IslRepository createIslRepository() {
        return new FermaIslRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON),
                (FermaFlowPathRepository) createFlowPathRepository(), islConfig);
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return new FermaLinkPropsRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new FermaSwitchRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SwitchConnectRepository createSwitchConnectRepository() {
        return new FermaSwitchConnectRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return new FermaTransitVlanRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return new FermaVxlanRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        return new FermaKildaFeatureTogglesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return new FermaFlowEventRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowEventActionRepository createFlowEventActionRepository() {
        return new FermaFlowEventActionRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.HISTORY));
    }

    @Override
    public FlowEventDumpRepository createFlowEventDumpRepository() {
        return new FermaFlowEventDumpRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.HISTORY));
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return new FermaBfdSessionRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        return new FermaKildaConfigurationRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        return new FermaSwitchPropertiesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SwitchConnectedDeviceRepository createSwitchConnectedDeviceRepository() {
        return new FermaSwitchConnectedDevicesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public PortEventRepository createPortEventRepository() {
        return new FermaPortEventRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.HISTORY));
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        return new FermaPortPropertiesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public PathSegmentRepository createPathSegmentRepository() {
        return new FermaPathSegmentRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON), createIslRepository());
    }

    @Override
    public ApplicationRepository createApplicationRepository() {
        return new FermaApplicationRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        return new FermaExclusionIdRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        return new FermaMirrorGroupRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SpeakerRepository createSpeakerRepository() {
        return new FermaSpeakerRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowMirrorPointsRepository createFlowMirrorPointsRepository() {
        return new FermaFlowMirrorPointsRepository(graphFactory, createFlowMirrorPathRepository(),
                transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowMirrorPathRepository createFlowMirrorPathRepository() {
        return new FermaFlowMirrorPathRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public LagLogicalPortRepository createLagLogicalPortRepository() {
        return new FermaLagLogicalPortRepository(graphFactory, createPhysicalPortRepository(),
                transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public PhysicalPortRepository createPhysicalPortRepository() {
        return new FermaPhysicalPortRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }
}
