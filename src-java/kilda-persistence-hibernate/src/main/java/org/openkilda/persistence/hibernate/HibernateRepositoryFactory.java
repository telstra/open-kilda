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

package org.openkilda.persistence.hibernate;

import org.openkilda.persistence.hibernate.repositories.HibernateHistoryFlowEventActionRepository;
import org.openkilda.persistence.hibernate.repositories.HibernateHistoryFlowEventDumpRepository;
import org.openkilda.persistence.hibernate.repositories.HibernateHistoryFlowEventRepository;
import org.openkilda.persistence.hibernate.repositories.HibernateHistoryPortEventRepository;
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

import org.hibernate.SessionFactory;

import java.util.function.Supplier;

public class HibernateRepositoryFactory implements RepositoryFactory {
    private final Supplier<SessionFactory> factorySupplier;

    private final TransactionManagerFactory transactionManagerFactory;

    public HibernateRepositoryFactory(
            Supplier<SessionFactory> factorySupplier, TransactionManagerFactory transactionManagerFactory) {
        this.factorySupplier = factorySupplier;
        this.transactionManagerFactory = transactionManagerFactory;
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public FlowPathRepository createFlowPathRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public FlowRepository createFlowRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public IslRepository createIslRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public SwitchConnectRepository createSwitchConnectRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return makeFlowEventRepository();
    }

    @Override
    public FlowEventActionRepository createFlowEventActionRepository() {
        return new HibernateHistoryFlowEventActionRepository(
                transactionManagerFactory.produce(TransactionArea.HISTORY), factorySupplier, makeFlowEventRepository());
    }

    @Override
    public FlowEventDumpRepository createFlowEventDumpRepository() {
        return new HibernateHistoryFlowEventDumpRepository(
                transactionManagerFactory.produce(TransactionArea.HISTORY), factorySupplier, makeFlowEventRepository());
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public SwitchConnectedDeviceRepository createSwitchConnectedDeviceRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public PortEventRepository createPortEventRepository() {
        return new HibernateHistoryPortEventRepository(
                transactionManagerFactory.produce(TransactionArea.HISTORY), factorySupplier);
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public PathSegmentRepository createPathSegmentRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public ApplicationRepository createApplicationRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public SpeakerRepository createSpeakerRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public FlowMirrorPointsRepository createFlowMirrorPointsRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public FlowMirrorPathRepository createFlowMirrorPathRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public LagLogicalPortRepository createLagLogicalPortRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    @Override
    public PhysicalPortRepository createPhysicalPortRepository() {
        throw new IllegalStateException("Repository not implemented on hibernate layer");
    }

    private HibernateHistoryFlowEventRepository makeFlowEventRepository() {
        return new HibernateHistoryFlowEventRepository(
                transactionManagerFactory.produce(TransactionArea.HISTORY), factorySupplier);
    }
}
