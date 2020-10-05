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

import org.openkilda.persistence.ferma.frames.FlowFrame;
import org.openkilda.persistence.ferma.repositories.FermaFlowRepository;
import org.openkilda.persistence.orientdb.OrientDbGraphFactory;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

/**
 * OrientDB implementation of {@link FlowRepository}.
 */
@Slf4j
public class OrientDbFlowRepository extends FermaFlowRepository {
    private final OrientDbGraphFactory orientDbGraphFactory;

    OrientDbFlowRepository(OrientDbGraphFactory orientDbGraphFactory, FlowPathRepository fermaFlowPathRepository,
                           TransactionManager transactionManager) {
        super(orientDbGraphFactory, fermaFlowPathRepository, transactionManager);
        this.orientDbGraphFactory = orientDbGraphFactory;
    }

    @Override
    public boolean exists(String flowId) {
        try (OGremlinResultSet results = orientDbGraphFactory.getOrientGraph().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? LIMIT 1",
                        FlowFrame.FRAME_LABEL, FlowFrame.FLOW_ID_PROPERTY), flowId)) {
            return results.iterator().hasNext();
        }
    }
}
