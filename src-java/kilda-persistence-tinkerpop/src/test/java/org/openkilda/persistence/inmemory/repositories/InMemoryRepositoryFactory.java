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
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.repositories.FermaFlowPathRepository;
import org.openkilda.persistence.ferma.repositories.FermaRepositoryFactory;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.ExclusionIdRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
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
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionManagerFactory;

/**
 * In-memory implementation of {@link RepositoryFactory}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryRepositoryFactory extends FermaRepositoryFactory {
    public InMemoryRepositoryFactory(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager,
                                     NetworkConfig networkConfig) {
        super(graphFactory, new DummyTransactionManagerFactory(transactionManager), networkConfig);
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return new InMemoryFlowCookieRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return new InMemoryFlowMeterRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowPathRepository createFlowPathRepository() {
        return new InMemoryFlowPathRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new InMemoryFlowRepository(graphFactory, createFlowPathRepository(),
                transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public IslRepository createIslRepository() {
        return new InMemoryIslRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON),
                (FermaFlowPathRepository) createFlowPathRepository(), islConfig);
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return new InMemoryLinkPropsRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new InMemorySwitchRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return new InMemoryTransitVlanRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return new InMemoryVxlanRepository(graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public KildaFeatureTogglesRepository createFeatureTogglesRepository() {
        return new InMemoryKildaFeatureTogglesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return new InMemoryFlowEventRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.HISTORY));
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return new InMemoryBfdSessionRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public KildaConfigurationRepository createKildaConfigurationRepository() {
        return new InMemoryKildaConfigurationRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public SwitchPropertiesRepository createSwitchPropertiesRepository() {
        return new InMemorySwitchPropertiesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public PortPropertiesRepository createPortPropertiesRepository() {
        return new InMemoryPortPropertiesRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public ExclusionIdRepository createExclusionIdRepository() {
        return new InMemoryExclusionIdRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    @Override
    public MirrorGroupRepository createMirrorGroupRepository() {
        return new InMemoryMirrorGroupRepository(
                graphFactory, transactionManagerFactory.produce(TransactionArea.COMMON));
    }

    private static class DummyTransactionManagerFactory implements TransactionManagerFactory {
        private final TransactionManager delegate;

        public DummyTransactionManagerFactory(TransactionManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public TransactionManager produce(TransactionArea area) {
            return delegate;
        }
    }
}
