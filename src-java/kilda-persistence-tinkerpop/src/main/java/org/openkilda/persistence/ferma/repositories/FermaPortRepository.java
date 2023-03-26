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

package org.openkilda.persistence.ferma.repositories;

import org.openkilda.model.Port;
import org.openkilda.model.Port.PortData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.PortFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.PortRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link PortRepository}.
 */
@Slf4j
public class FermaPortRepository extends FermaGenericRepository<Port, PortData, PortFrame>
        implements PortRepository {
    public FermaPortRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Collection<Port> getAllBySwitchId(SwitchId switchId) {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(PortFrame.FRAME_LABEL)
                        .has(PortFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(PortFrame.class).stream()
                .map(Port::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Port> getBySwitchIdAndPort(SwitchId switchId, int portNumber) {
        List<? extends PortFrame> portFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(PortFrame.FRAME_LABEL)
                        .has(PortFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(PortFrame.PORT_NO_PROPERTY, portNumber))
                .toListExplicit(PortFrame.class);

        return portFrames.isEmpty() ? Optional.empty() : Optional.of(portFrames.get(0)).map(Port::new);
    }

    @Override
    protected PortFrame doAdd(PortData data) {
        PortFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                PortFrame.FRAME_LABEL, PortFrame.class);
        Port.PortCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(PortFrame frame) {
        frame.remove();
    }

    @Override
    protected PortData doDetach(Port entity, PortFrame frame) {
        return Port.PortCloner.INSTANCE.deepCopy(frame);
    }
}
