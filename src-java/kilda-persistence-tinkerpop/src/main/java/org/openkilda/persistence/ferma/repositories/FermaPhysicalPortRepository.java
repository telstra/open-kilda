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

import org.openkilda.model.PhysicalPort;
import org.openkilda.model.PhysicalPort.PhysicalPortData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.PhysicalPortFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.tx.TransactionManager;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link PhysicalPortRepository}.
 */
public class FermaPhysicalPortRepository
        extends FermaGenericRepository<PhysicalPort, PhysicalPortData, PhysicalPortFrame>
        implements PhysicalPortRepository {
    FermaPhysicalPortRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    public Collection<PhysicalPort> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(PhysicalPortFrame.FRAME_LABEL))
                .toListExplicit(PhysicalPortFrame.class).stream()
                .map(PhysicalPort::new)
                .collect(Collectors.toList());
    }

    @Override
    public Set<Integer> findPortNumbersBySwitchId(SwitchId switchId) {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(PhysicalPortFrame.FRAME_LABEL)
                .has(PhysicalPortFrame.SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(PhysicalPortFrame.class).stream()
                .map(PhysicalPortFrame::getPortNumber)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<PhysicalPort> findBySwitchIdAndPortNumber(SwitchId switchId, int portNumber) {
        List<? extends PhysicalPortFrame> port = framedGraph().traverse(g -> g.V()
                        .hasLabel(PhysicalPortFrame.FRAME_LABEL)
                        .has(PhysicalPortFrame.SWITCH_ID_PROPERTY,
                                SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(PhysicalPortFrame.PORT_NUMBER_PROPERTY, portNumber))
                .toListExplicit(PhysicalPortFrame.class);
        return port.isEmpty() ? Optional.empty() : Optional.of(port.get(0)).map(PhysicalPort::new);
    }

    @Override
    protected PhysicalPortFrame doAdd(PhysicalPortData data) {
        PhysicalPortFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                PhysicalPortFrame.FRAME_LABEL, PhysicalPortFrame.class);
        PhysicalPort.PhysicalPortCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(PhysicalPortFrame frame) {
        frame.remove();
    }

    @Override
    protected PhysicalPortData doDetach(PhysicalPort entity, PhysicalPortFrame frame) {
        return PhysicalPort.PhysicalPortCloner.INSTANCE.deepCopy(frame, entity.getLagLogicalPort());
    }
}
