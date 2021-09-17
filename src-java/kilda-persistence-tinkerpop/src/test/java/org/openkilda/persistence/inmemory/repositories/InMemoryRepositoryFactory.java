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

package org.openkilda.persistence.inmemory.repositories;

import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.ferma.repositories.FermaRepositoryFactory;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.ExclusionIdRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

/**
 * In-memory implementation of {@link RepositoryFactory}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryRepositoryFactory extends FermaRepositoryFactory {
    private final InMemoryGraphPersistenceImplementation implementation;

    public InMemoryRepositoryFactory(
            InMemoryGraphPersistenceImplementation implementation, NetworkConfig networkConfig) {
        super(implementation, networkConfig);
        this.implementation = implementation;
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return new InMemoryFlowCookieRepository(implementation);
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return new InMemoryFlowMeterRepository(implementation);
    }

    @Override
    public InMemoryFlowPathRepository createFlowPathRepository() {
        return new InMemoryFlowPathRepository(implementation);
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new InMemoryFlowRepository(implementation, createFlowPathRepository());
    }

    @Override
    public IslRepository createIslRepository() {
        return new InMemoryIslRepository(
                implementation, createFlowPathRepository(), islConfig);
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return new InMemoryLinkPropsRepository(implementation);
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new InMemorySwitchRepository(implementation);
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return new InMemoryTransitVlanRepository(implementation);
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return new InMemoryVxlanRepository(implementation);
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        return new InMemoryKildaFeatureTogglesRepository(implementation);
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return new InMemoryFlowEventRepository(implementation);
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return new InMemoryBfdSessionRepository(implementation);
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        return new InMemoryKildaConfigurationRepository(implementation);
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        return new InMemorySwitchPropertiesRepository(implementation);
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        return new InMemoryPortPropertiesRepository(implementation);
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        return new InMemoryExclusionIdRepository(implementation);
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        return new InMemoryMirrorGroupRepository(implementation);
    }
}
