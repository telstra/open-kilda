/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.KildaFeatureToggles.KildaFeatureTogglesData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.KildaFeatureTogglesFrame;
import org.openkilda.persistence.ferma.repositories.FermaKildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.tx.TransactionManager;

/**
 * In-memory implementation of {@link KildaFeatureTogglesRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryKildaFeatureTogglesRepository extends FermaKildaFeatureTogglesRepository {
    InMemoryKildaFeatureTogglesRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    protected KildaFeatureTogglesFrame doAdd(KildaFeatureTogglesData data) {
        if (find().isPresent()) {
            throw new ConstraintViolationException("Unable to create " + KildaFeatureTogglesFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
