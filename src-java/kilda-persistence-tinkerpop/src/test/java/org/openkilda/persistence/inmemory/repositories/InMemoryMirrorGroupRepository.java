/* Copyright 2019 Telstra Open Source
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

import org.openkilda.model.MirrorGroup.MirrorGroupData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.frames.MirrorGroupFrame;
import org.openkilda.persistence.ferma.repositories.FermaMirrorGroupRepository;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.MirrorGroupRepository;

/**
 * In-memory implementation of {@link MirrorGroupRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryMirrorGroupRepository extends FermaMirrorGroupRepository {
    public InMemoryMirrorGroupRepository(InMemoryGraphPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    protected MirrorGroupFrame doAdd(MirrorGroupData data) {
        if (framedGraph().traverse(input -> input.V()
                .hasLabel(MirrorGroupFrame.FRAME_LABEL)
                .has(MirrorGroupFrame.SWITCH_ID_PROPERTY, data.getSwitchId())
                .has(MirrorGroupFrame.GROUP_ID_PROPERTY, data.getGroupId())
                .has(MirrorGroupFrame.MIRROR_GROUP_TYPE_PROPERTY, data.getMirrorGroupType())
                .has(MirrorGroupFrame.MIRROR_DIRECTION_PROPERTY, data.getMirrorDirection()))
                .getRawTraversal().hasNext()) {
            throw new ConstraintViolationException("Unable to create " + MirrorGroupFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
