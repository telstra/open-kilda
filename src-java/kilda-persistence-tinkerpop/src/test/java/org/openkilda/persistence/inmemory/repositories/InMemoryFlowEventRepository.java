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

import org.openkilda.model.history.FlowEvent.FlowEventData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.frames.FlowEventFrame;
import org.openkilda.persistence.ferma.repositories.FermaFlowEventRepository;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

/**
 * In-memory implementation of {@link FlowEventRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryFlowEventRepository extends FermaFlowEventRepository {
    public InMemoryFlowEventRepository(InMemoryGraphPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    protected FlowEventFrame doAdd(FlowEventData data) {
        if (existsByTaskId(data.getTaskId())) {
            throw new ConstraintViolationException("Unable to create " + FlowEventFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
