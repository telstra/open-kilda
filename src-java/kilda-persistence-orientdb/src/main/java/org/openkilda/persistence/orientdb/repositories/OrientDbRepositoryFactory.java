/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.orientdb.repositories;

import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.ferma.repositories.FermaRepositoryFactory;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * OrientDB implementation of {@link RepositoryFactory}.
 */
@Slf4j
public class OrientDbRepositoryFactory extends FermaRepositoryFactory {
    private final OrientDbPersistenceImplementation implementation;
    private final GraphSupplier graphSupplier;

    public OrientDbRepositoryFactory(OrientDbPersistenceImplementation implementation, NetworkConfig networkConfig) {
        super(implementation, networkConfig);
        this.implementation = implementation;
        graphSupplier = new GraphSupplier(implementation);
    }

    @Override
    public FlowCookieRepository createFlowCookieRepository() {
        return new OrientDbFlowCookieRepository(implementation, graphSupplier);
    }

    @Override
    public FlowMeterRepository createFlowMeterRepository() {
        return new OrientDbFlowMeterRepository(implementation, graphSupplier);
    }

    @Override
    public OrientDbFlowPathRepository createFlowPathRepository() {
        return new OrientDbFlowPathRepository(implementation, graphSupplier);
    }

    @Override
    public FlowRepository createFlowRepository() {
        return new OrientDbFlowRepository(implementation, graphSupplier, createFlowPathRepository());
    }

    @Override
    public IslRepository createIslRepository() {
        return new OrientDbIslRepository(implementation, graphSupplier, createFlowPathRepository(), islConfig);
    }

    @Override
    public SwitchRepository createSwitchRepository() {
        return new OrientDbSwitchRepository(implementation, graphSupplier);
    }

    @Override
    public TransitVlanRepository createTransitVlanRepository() {
        return new OrientDbTransitVlanRepository(implementation, graphSupplier);
    }

    @Override
    public VxlanRepository createVxlanRepository() {
        return new OrientDbVxlanRepository(implementation, graphSupplier);
    }

    @Override
    public FlowEventRepository createFlowEventRepository() {
        return new OrientDbFlowEventRepository(implementation, graphSupplier);
    }

    @Override
    public BfdSessionRepository createBfdSessionRepository() {
        return new OrientDbBfdSessionRepository(implementation, graphSupplier);
    }

    @Override
    public PathSegmentRepository createPathSegmentRepository() {
        return new OrientDbPathSegmentRepository(implementation, graphSupplier, createIslRepository());
    }
}
