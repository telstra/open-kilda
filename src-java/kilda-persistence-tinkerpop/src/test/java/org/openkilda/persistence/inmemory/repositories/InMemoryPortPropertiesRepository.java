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

import org.openkilda.model.PortProperties.PortPropertiesData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.frames.PortPropertiesFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.ferma.repositories.FermaPortPropertiesRepository;
import org.openkilda.persistence.inmemory.InMemoryGraphPersistenceImplementation;
import org.openkilda.persistence.repositories.PortPropertiesRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory implementation of {@link PortPropertiesRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
@Slf4j
public class InMemoryPortPropertiesRepository extends FermaPortPropertiesRepository {
    InMemoryPortPropertiesRepository(InMemoryGraphPersistenceImplementation implementation) {
        super(implementation);
    }

    @Override
    protected PortPropertiesFrame doAdd(PortPropertiesData data) {
        if (getBySwitchIdAndPort(data.getSwitchId(), data.getPort()).isPresent()
                || framedGraph().traverse(input -> input.V()
                .hasLabel(PortPropertiesFrame.FRAME_LABEL)
                .has(PortPropertiesFrame.SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(data.getSwitchId()))
                .has(PortPropertiesFrame.PORT_NO_PROPERTY, data.getPort()))
                .getRawTraversal().hasNext()) {
            throw new ConstraintViolationException("Unable to create " + PortPropertiesFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
