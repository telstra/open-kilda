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

package org.openkilda.persistence.mixture.sql.history;

import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.hibernate.HibernateRepositoryFactory;
import org.openkilda.persistence.orientdb.OrientDbGraphFactory;
import org.openkilda.persistence.orientdb.repositories.OrientDbRepositoryFactory;
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
import org.openkilda.persistence.tx.TransactionManagerFactory;

import org.hibernate.SessionFactory;

import java.util.function.Supplier;

public class SqlHistoryRepositoryFactory implements RepositoryFactory {
    private final OrientDbRepositoryFactory orientRepositoryFactory;

    private final HibernateRepositoryFactory hibernateRepositoryFactory;

    public SqlHistoryRepositoryFactory(
            OrientDbGraphFactory orientDbGraphFactory,
            NetworkConfig networkConfig, TransactionManagerFactory transactionManagerFactory,
            Supplier<SessionFactory> factorySupplier) {
        orientRepositoryFactory = new OrientDbRepositoryFactory(
                orientDbGraphFactory, transactionManagerFactory, networkConfig);
        hibernateRepositoryFactory = new HibernateRepositoryFactory(factorySupplier, transactionManagerFactory);
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return orientRepositoryFactory.createFlowCookieRepository();
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return orientRepositoryFactory.createFlowMeterRepository();
    }

    @Override
    public FlowPathRepository createFlowPathRepository() {
        return orientRepositoryFactory.createFlowPathRepository();
    }

    @Override
    public FlowRepository createFlowRepository() {
        return orientRepositoryFactory.createFlowRepository();
    }

    @Override
    public IslRepository createIslRepository() {
        return orientRepositoryFactory.createIslRepository();
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return orientRepositoryFactory.createLinkPropsRepository();
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return orientRepositoryFactory.createSwitchRepository();
    }

    @Override
    public SwitchConnectRepository createSwitchConnectRepository() {
        return orientRepositoryFactory.createSwitchConnectRepository();
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return orientRepositoryFactory.createTransitVlanRepository();
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return orientRepositoryFactory.createVxlanRepository();
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        return orientRepositoryFactory.createFeatureTogglesRepository();
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return hibernateRepositoryFactory.createFlowEventRepository();
    }

    @Override
    public FlowEventActionRepository createFlowEventActionRepository() {
        return hibernateRepositoryFactory.createFlowEventActionRepository();
    }

    @Override
    public FlowEventDumpRepository createFlowEventDumpRepository() {
        return hibernateRepositoryFactory.createFlowEventDumpRepository();
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return orientRepositoryFactory.createBfdSessionRepository();
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        return orientRepositoryFactory.createKildaConfigurationRepository();
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        return orientRepositoryFactory.createSwitchPropertiesRepository();
    }

    @Override
    public SwitchConnectedDeviceRepository createSwitchConnectedDeviceRepository() {
        return orientRepositoryFactory.createSwitchConnectedDeviceRepository();
    }

    @Override
    public PortEventRepository createPortEventRepository() {
        return hibernateRepositoryFactory.createPortEventRepository();
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        return orientRepositoryFactory.createPortPropertiesRepository();
    }

    @Override
    public PathSegmentRepository createPathSegmentRepository() {
        return orientRepositoryFactory.createPathSegmentRepository();
    }

    @Override
    public ApplicationRepository createApplicationRepository() {
        return orientRepositoryFactory.createApplicationRepository();
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        return orientRepositoryFactory.createExclusionIdRepository();
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        return orientRepositoryFactory.createMirrorGroupRepository();
    }

    @Override
    public SpeakerRepository createSpeakerRepository() {
        return orientRepositoryFactory.createSpeakerRepository();
    }

    @Override
    public FlowMirrorPointsRepository createFlowMirrorPointsRepository() {
        return orientRepositoryFactory.createFlowMirrorPointsRepository();
    }

    @Override
    public FlowMirrorPathRepository createFlowMirrorPathRepository() {
        return orientRepositoryFactory.createFlowMirrorPathRepository();
    }

    @Override
    public LagLogicalPortRepository createLagLogicalPortRepository() {
        return orientRepositoryFactory.createLagLogicalPortRepository();
    }

    @Override
    public PhysicalPortRepository createPhysicalPortRepository() {
        return orientRepositoryFactory.createPhysicalPortRepository();
    }
}
