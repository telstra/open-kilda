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

import org.openkilda.model.LinkProps.LinkPropsData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.frames.LinkPropsFrame;
import org.openkilda.persistence.ferma.repositories.FermaLinkPropsRepository;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.LinkPropsRepository;

/**
 * In-memory implementation of {@link LinkPropsRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryLinkPropsRepository extends FermaLinkPropsRepository {
    InMemoryLinkPropsRepository(InMemoryGraphPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    protected LinkPropsFrame doAdd(LinkPropsData data) {
        if (!findByEndpoints(data.getSrcSwitchId(), data.getSrcPort(),
                data.getDstSwitchId(), data.getDstPort()).isEmpty()) {
            throw new ConstraintViolationException("Unable to create " + LinkPropsFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
