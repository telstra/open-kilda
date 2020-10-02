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

import org.openkilda.model.BfdSession.BfdSessionData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.BfdSessionFrame;
import org.openkilda.persistence.ferma.repositories.FermaBfdSessionRepository;
import org.openkilda.persistence.repositories.BfdSessionRepository;
import org.openkilda.persistence.tx.TransactionManager;

/**
 * In-memory implementation of {@link BfdSessionRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryBfdSessionRepository extends FermaBfdSessionRepository {
    InMemoryBfdSessionRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    protected BfdSessionFrame doAdd(BfdSessionData data) {
        if (framedGraph().traverse(input -> input.V()
                .hasLabel(BfdSessionFrame.FRAME_LABEL)
                .has(BfdSessionFrame.DISCRIMINATOR_PROPERTY, data.getDiscriminator()))
                .getRawTraversal().hasNext()) {
            throw new ConstraintViolationException("Unable to create " + BfdSessionFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
