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

import org.openkilda.model.FlowCookie.FlowCookieData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.frames.FlowCookieFrame;
import org.openkilda.persistence.ferma.repositories.FermaFlowCookieRepository;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.FlowCookieRepository;

/**
 * In-memory implementation of {@link FlowCookieRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryFlowCookieRepository extends FermaFlowCookieRepository {
    public InMemoryFlowCookieRepository(InMemoryGraphPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    protected FlowCookieFrame doAdd(FlowCookieData data) {
        if (exists(data.getUnmaskedCookie())) {
            throw new ConstraintViolationException("Unable to create " + FlowCookieFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
