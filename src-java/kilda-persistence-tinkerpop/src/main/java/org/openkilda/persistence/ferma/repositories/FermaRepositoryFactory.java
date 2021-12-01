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
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.repositories.ApplicationRepository;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.ExclusionIdRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowStatsRepository;
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
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.PortEventRepository;

import java.time.Duration;

/**
 * Ferma (Tinkerpop) implementation of {@link RepositoryFactory}.
 */
public class FermaRepositoryFactory implements RepositoryFactory {
    protected final FermaPersistentImplementation implementation;

    protected final IslConfig islConfig;

    public FermaRepositoryFactory(FermaPersistentImplementation implementation, NetworkConfig networkConfig) {
        this.implementation = implementation;

        this.islConfig = IslConfig.builder()
                .unstableIslTimeout(Duration.ofSeconds(networkConfig.getIslUnstableTimeoutSec()))
                .build();
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return new FermaFlowCookieRepository(implementation);
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return new FermaFlowMeterRepository(implementation);
    }

    @Override
    public FermaFlowPathRepository createFlowPathRepository() {
        return new FermaFlowPathRepository(implementation);
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new FermaFlowRepository(implementation, createFlowPathRepository());
    }

    @Override
    public IslRepository createIslRepository() {
        return new FermaIslRepository(
                implementation, createFlowPathRepository(), islConfig);
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return new FermaLinkPropsRepository(implementation);
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new FermaSwitchRepository(implementation);
    }

    @Override
    public SwitchConnectRepository createSwitchConnectRepository() {
        return new FermaSwitchConnectRepository(implementation);
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return new FermaTransitVlanRepository(implementation);
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return new FermaVxlanRepository(implementation);
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        return new FermaKildaFeatureTogglesRepository(implementation);
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return new FermaFlowEventRepository(implementation);
    }

    @Override
    public FlowEventActionRepository createFlowEventActionRepository() {
        return new FermaFlowEventActionRepository(implementation);
    }

    @Override
    public FlowEventDumpRepository createFlowEventDumpRepository() {
        return new FermaFlowEventDumpRepository(implementation);
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return new FermaBfdSessionRepository(implementation);
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        return new FermaKildaConfigurationRepository(implementation);
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        return new FermaSwitchPropertiesRepository(implementation);
    }

    @Override
    public FlowStatsRepository createFlowStatsRepository() {
        return new FermaFlowStatsRepository(implementation);
    }

    @Override
    public SwitchConnectedDeviceRepository createSwitchConnectedDeviceRepository() {
        return new FermaSwitchConnectedDevicesRepository(implementation);
    }

    @Override
    public PortEventRepository createPortEventRepository() {
        return new FermaPortEventRepository(implementation);
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        return new FermaPortPropertiesRepository(implementation);
    }

    @Override
    public PathSegmentRepository createPathSegmentRepository() {
        return new FermaPathSegmentRepository(implementation, createIslRepository());
    }

    @Override
    public ApplicationRepository createApplicationRepository() {
        return new FermaApplicationRepository(implementation);
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        return new FermaExclusionIdRepository(implementation);
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        return new FermaMirrorGroupRepository(implementation);
    }

    @Override
    public SpeakerRepository createSpeakerRepository() {
        return new FermaSpeakerRepository(implementation);
    }

    @Override
    public FlowMirrorPointsRepository createFlowMirrorPointsRepository() {
        return new FermaFlowMirrorPointsRepository(
                implementation, createFlowMirrorPathRepository());
    }

    @Override
    public FlowMirrorPathRepository createFlowMirrorPathRepository() {
        return new FermaFlowMirrorPathRepository(implementation);
    }

    @Override
    public LagLogicalPortRepository createLagLogicalPortRepository() {
        return new FermaLagLogicalPortRepository(
                implementation, createPhysicalPortRepository());
    }

    @Override
    public PhysicalPortRepository createPhysicalPortRepository() {
        return new FermaPhysicalPortRepository(implementation);
    }

    @Override
    public YFlowRepository createYFlowRepository() {
        return new FermaYFlowRepository(implementation);
    }
}
