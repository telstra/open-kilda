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

import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

/**
 * Neo4J OGM implementation of {@link RepositoryFactory}.
 */
public class Neo4jRepositoryFactory implements RepositoryFactory {

    private final Neo4jSessionFactory sessionFactory;

    public Neo4jRepositoryFactory(Neo4jSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new Neo4jFlowRepository(sessionFactory);
    }

    @Override
    public FlowSegmentRepository createFlowSegmentRepository() {
        return new Neo4jFlowSegmentRepository(sessionFactory);
    }

    @Override
    public IslRepository createIslRepository() {
        return new Neo4jIslRepository(sessionFactory);
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new Neo4jSwitchRepository(sessionFactory);
    }

    @Override
    public LinkPropsRepository createLinkPropsRepository() {
        return new Neo4jLinkPropsRepository(sessionFactory);
    }
}
