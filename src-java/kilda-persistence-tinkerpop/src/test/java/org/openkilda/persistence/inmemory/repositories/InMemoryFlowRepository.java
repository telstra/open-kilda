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

package org.openkilda.persistence.inmemory.repositories;

import org.openkilda.model.Flow.FlowData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FlowFrame;
import org.openkilda.persistence.ferma.repositories.FermaFlowRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory implementation of {@link FlowRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
@Slf4j
public class InMemoryFlowRepository extends FermaFlowRepository {
    public InMemoryFlowRepository(FramedGraphFactory<?> graphFactory,
                                  FlowPathRepository flowPathRepository,
                                  TransactionManager transactionManager) {
        super(graphFactory, flowPathRepository, transactionManager);
    }

    @Override
    protected FlowFrame doAdd(FlowData data) {
        if (exists(data.getFlowId())) {
            throw new ConstraintViolationException("Unable to create " + FlowFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
