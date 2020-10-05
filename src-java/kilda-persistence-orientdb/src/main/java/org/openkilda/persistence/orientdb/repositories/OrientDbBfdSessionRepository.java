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

import static java.lang.String.format;

import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.BfdSessionFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.ferma.repositories.FermaBfdSessionRepository;
import org.openkilda.persistence.orientdb.OrientDbGraphFactory;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

/**
 * OrientDB implementation of {@link BfdSessionRepository}.
 */
public class OrientDbBfdSessionRepository extends FermaBfdSessionRepository {
    private final OrientDbGraphFactory orientDbGraphFactory;

    OrientDbBfdSessionRepository(OrientDbGraphFactory orientDbGraphFactory,
                                 TransactionManager transactionManager) {
        super(orientDbGraphFactory, transactionManager);
        this.orientDbGraphFactory = orientDbGraphFactory;
    }

    @Override
    public boolean exists(SwitchId switchId, Integer port) {
        try (OGremlinResultSet results = orientDbGraphFactory.getOrientGraph().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? AND %s = ? LIMIT 1",
                        BfdSessionFrame.FRAME_LABEL, BfdSessionFrame.SWITCH_ID_PROPERTY, BfdSessionFrame.PORT_PROPERTY),
                SwitchIdConverter.INSTANCE.toGraphProperty(switchId), port)) {
            return results.iterator().hasNext();
        }
    }
}
