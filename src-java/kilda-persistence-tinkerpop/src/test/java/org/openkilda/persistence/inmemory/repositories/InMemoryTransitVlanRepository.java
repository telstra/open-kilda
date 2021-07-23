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

import org.openkilda.model.TransitVlan.TransitVlanData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.frames.TransitVlanFrame;
import org.openkilda.persistence.ferma.repositories.FermaTransitVlanRepository;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.TransitVlanRepository;

/**
 * In-memory implementation of {@link TransitVlanRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryTransitVlanRepository extends FermaTransitVlanRepository {
    public InMemoryTransitVlanRepository(InMemoryGraphPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    protected TransitVlanFrame doAdd(TransitVlanData data) {
        if (exists(data.getVlan())) {
            throw new ConstraintViolationException("Unable to create " + TransitVlanFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
