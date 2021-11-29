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

package org.openkilda.persistence.repositories;

import org.openkilda.persistence.PersistenceArea;
import org.openkilda.persistence.PersistenceImplementation;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.repositories.history.PortEventRepository;

public class RepositoryFactoryProxy implements RepositoryFactory {
    private final PersistenceManager manager;

    public RepositoryFactoryProxy(PersistenceManager manager) {
        this.manager = manager;
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return resolve(FlowCookieRepository.class).createFlowCookieRepository();
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return resolve(FlowMeterRepository.class).createFlowMeterRepository();
    }

    @Override
    public FlowPathRepository createFlowPathRepository() {
        return resolve(FlowPathRepository.class).createFlowPathRepository();
    }

    @Override
    public FlowRepository createFlowRepository() {
        return resolve(FlowRepository.class).createFlowRepository();
    }

    @Override
    public IslRepository createIslRepository() {
        return resolve(IslRepository.class).createIslRepository();
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return resolve(LinkPropsRepository.class).createLinkPropsRepository();
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return resolve(SwitchRepository.class).createSwitchRepository();
    }

    @Override
    public SwitchConnectRepository createSwitchConnectRepository() {
        return resolve(SwitchConnectRepository.class).createSwitchConnectRepository();
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return resolve(TransitVlanRepository.class).createTransitVlanRepository();
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return resolve(VxlanRepository.class).createVxlanRepository();
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        return resolve(KildaFeatureTogglesRepository.class).createFeatureTogglesRepository();
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return resolve(FlowEventRepository.class).createFlowEventRepository();
    }

    @Override
    public FlowEventActionRepository createFlowEventActionRepository() {
        return resolve(FlowEventActionRepository.class).createFlowEventActionRepository();
    }

    @Override
    public FlowEventDumpRepository createFlowEventDumpRepository() {
        return resolve(FlowEventDumpRepository.class).createFlowEventDumpRepository();
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return resolve(BfdSessionRepository.class).createBfdSessionRepository();
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        return resolve(KildaConfigurationRepository.class).createKildaConfigurationRepository();
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        return resolve(SwitchPropertiesRepository.class).createSwitchPropertiesRepository();
    }

    @Override
    public FlowStatsRepository createFlowStatsRepository() {
        return resolve(FlowStatsRepository.class).createFlowStatsRepository();
    }

    @Override
    public SwitchConnectedDeviceRepository createSwitchConnectedDeviceRepository() {
        return resolve(SwitchConnectedDeviceRepository.class).createSwitchConnectedDeviceRepository();
    }

    @Override
    public PortEventRepository createPortEventRepository() {
        return resolve(PortEventRepository.class).createPortEventRepository();
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        return resolve(PortPropertiesRepository.class).createPortPropertiesRepository();
    }

    @Override
    public PathSegmentRepository createPathSegmentRepository() {
        return resolve(PathSegmentRepository.class).createPathSegmentRepository();
    }

    @Override
    public ApplicationRepository createApplicationRepository() {
        return resolve(ApplicationRepository.class).createApplicationRepository();
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        return resolve(ExclusionIdRepository.class).createExclusionIdRepository();
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        return resolve(MirrorGroupRepository.class).createMirrorGroupRepository();
    }

    @Override
    public SpeakerRepository createSpeakerRepository() {
        return resolve(SpeakerRepository.class).createSpeakerRepository();
    }

    @Override
    public FlowMirrorPointsRepository createFlowMirrorPointsRepository() {
        return resolve(FlowMirrorPointsRepository.class).createFlowMirrorPointsRepository();
    }

    @Override
    public FlowMirrorPathRepository createFlowMirrorPathRepository() {
        return resolve(FlowMirrorPathRepository.class).createFlowMirrorPathRepository();
    }

    @Override
    public LagLogicalPortRepository createLagLogicalPortRepository() {
        return resolve(LagLogicalPortRepository.class).createLagLogicalPortRepository();
    }

    @Override
    public PhysicalPortRepository createPhysicalPortRepository() {
        return resolve(PhysicalPortRepository.class).createPhysicalPortRepository();
    }

    @Override
    public YFlowRepository createYFlowRepository() {
        return resolve(YFlowRepository.class).createYFlowRepository();
    }

    private RepositoryFactory resolve(Class<?> repositoryClass) {
        PersistenceArea area = RepositoryAreaBinding.INSTANCE.lookup(repositoryClass);
        PersistenceImplementation implementation = manager.getImplementation(area);
        return implementation.getRepositoryFactory();
    }
}
