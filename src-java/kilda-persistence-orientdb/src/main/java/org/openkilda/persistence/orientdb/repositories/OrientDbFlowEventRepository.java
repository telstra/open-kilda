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

import org.openkilda.persistence.ferma.frames.FlowEventFrame;
import org.openkilda.persistence.ferma.repositories.FermaFlowEventRepository;
import org.openkilda.persistence.orientdb.OrientDbGraphFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

/**
 * OrientDB implementation of {@link FlowEventRepository}.
 */
public class OrientDbFlowEventRepository extends FermaFlowEventRepository {
    private final OrientDbGraphFactory orientDbGraphFactory;

    OrientDbFlowEventRepository(OrientDbGraphFactory orientDbGraphFactory, TransactionManager transactionManager) {
        super(orientDbGraphFactory, transactionManager);
        this.orientDbGraphFactory = orientDbGraphFactory;
    }

    @Override
    public boolean existsByTaskId(String taskId) {
        try (OGremlinResultSet results = orientDbGraphFactory.getOrientGraph().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? LIMIT 1",
                        FlowEventFrame.FRAME_LABEL, FlowEventFrame.TASK_ID_PROPERTY), taskId)) {
            return results.iterator().hasNext();
        }
    }
}
