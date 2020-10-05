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

import org.openkilda.model.IslConfig;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.IslFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.ferma.repositories.FermaIslRepository;
import org.openkilda.persistence.orientdb.OrientDbGraphFactory;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

/**
 * OrientDB implementation of {@link IslRepository}.
 */
@Slf4j
public class OrientDbIslRepository extends FermaIslRepository {
    private final OrientDbGraphFactory orientDbGraphFactory;

    public OrientDbIslRepository(OrientDbGraphFactory orientDbGraphFactory,
                                 TransactionManager transactionManager, IslConfig islConfig) {
        super(orientDbGraphFactory, transactionManager, islConfig);
        this.orientDbGraphFactory = orientDbGraphFactory;
    }

    @Override
    public boolean existsByEndpoint(SwitchId switchId, int port) {
        String switchIdAsStr = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        try (OGremlinResultSet results = orientDbGraphFactory.getOrientGraph().querySql(
                format("SELECT @rid FROM %s WHERE (%s = ? AND %s = ?) OR (%s = ? AND %s = ?) LIMIT 1",
                        IslFrame.FRAME_LABEL, IslFrame.SRC_SWITCH_ID_PROPERTY, IslFrame.SRC_PORT_PROPERTY,
                        IslFrame.DST_SWITCH_ID_PROPERTY, IslFrame.DST_PORT_PROPERTY),
                switchIdAsStr, port, switchIdAsStr, port)) {
            return results.iterator().hasNext();
        }
    }
}
