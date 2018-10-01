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

import org.openkilda.persistence.neo4j.Neo4jSessionFactory;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

/**
 * Neo4J OGM implementation of {@link RepositoryFactory}.
 */
public class RepositoryFactoryImpl implements RepositoryFactory {

    private final Neo4jSessionFactory sessionFactory;

    public RepositoryFactoryImpl(Neo4jSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public FlowRepository getFlowRepository() {
        return new FlowRepositoryImpl(sessionFactory);
    }

    @Override
    public FlowSegmentRepository getFlowSegmentRepository() {
        return new FlowSegmentRepositoryImpl(sessionFactory);
    }

    @Override
    public IslRepository getIslRepository() {
        return new IslRepositoryImpl(sessionFactory);
    }

    @Override
    public SwitchRepository getSwitchRepository() {
        return new SwitchRepositoryImpl(sessionFactory);
    }
}
