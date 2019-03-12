/* Copyright 2018 Telstra Open Source
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

package org.openkilda.persistence.repositories.impl;

import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPortRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;

/**
 * Neo4J OGM implementation of {@link RepositoryFactory}.
 */
public class Neo4jRepositoryFactory implements RepositoryFactory {

    private final Neo4jSessionFactory sessionFactory;
    private final TransactionManager transactionManager;

    public Neo4jRepositoryFactory(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        this.sessionFactory = sessionFactory;
        this.transactionManager = transactionManager;
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return new Neo4jFlowCookieRepository(sessionFactory, transactionManager);
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return new Neo4jFlowMeterRepository(sessionFactory, transactionManager);
    }

    @Override
    public FlowPathRepository createFlowPathRepository() {
        return new Neo4jFlowPathRepository(sessionFactory, transactionManager);
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new Neo4jFlowRepository(sessionFactory, transactionManager);
    }

    @Override
    public IslRepository createIslRepository() {
        return new Neo4jIslRepository(sessionFactory, transactionManager);
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return new Neo4jLinkPropsRepository(sessionFactory, transactionManager);
    }

    @Override
    public SwitchPortRepository createSwitchPortRepository() {
        return new Neo4jSwitchPortRepository(sessionFactory, transactionManager);
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new Neo4jSwitchRepository(sessionFactory, transactionManager);
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return new Neo4jTransitVlanRepository(sessionFactory, transactionManager);
    }

    @Override
    public FeatureTogglesRepository createFeatureTogglesRepository() {
        return new Neo4jFeatureTogglesRepository(sessionFactory, transactionManager);
    }
}
