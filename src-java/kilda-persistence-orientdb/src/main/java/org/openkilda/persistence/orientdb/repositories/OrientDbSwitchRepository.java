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
import org.openkilda.persistence.ferma.frames.SwitchFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.ferma.repositories.FermaSwitchRepository;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

/**
 * OrientDB implementation of {@link SwitchRepository}.
 */
public class OrientDbSwitchRepository extends FermaSwitchRepository {
    private final GraphSupplier graphSupplier;

    OrientDbSwitchRepository(OrientDbPersistenceImplementation implementation, GraphSupplier graphSupplier) {
        super(implementation);
        this.graphSupplier = graphSupplier;
    }

    @Override
    public boolean exists(SwitchId switchId) {
        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? LIMIT 1",
                        SwitchFrame.FRAME_LABEL, SwitchFrame.SWITCH_ID_PROPERTY),
                SwitchIdConverter.INSTANCE.toGraphProperty(switchId))) {
            return results.iterator().hasNext();
        }
    }
}
