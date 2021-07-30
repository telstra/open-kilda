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

import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.LagLogicalPort.LagLogicalPortData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.LagLogicalPortFrame;
import org.openkilda.persistence.ferma.frames.PhysicalPortFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link LagLogicalPortRepository}.
 */
@Slf4j
public class FermaLagLogicalPortRepository
        extends FermaGenericRepository<LagLogicalPort, LagLogicalPortData, LagLogicalPortFrame>
        implements LagLogicalPortRepository {
    protected final PhysicalPortRepository physicalPortRepository;

    public FermaLagLogicalPortRepository(FramedGraphFactory<?> graphFactory,
                                         PhysicalPortRepository physicalPortRepository,
                                         TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
        this.physicalPortRepository = physicalPortRepository;
    }

    @Override
    public Collection<LagLogicalPort> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(LagLogicalPortFrame.FRAME_LABEL))
                .toListExplicit(LagLogicalPortFrame.class).stream()
                .map(LagLogicalPort::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<LagLogicalPort> findBySwitchId(SwitchId switchId) {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(LagLogicalPortFrame.FRAME_LABEL)
                        .has(LagLogicalPortFrame.SWITCH_ID_PROPERTY,
                                SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(LagLogicalPortFrame.class).stream()
                .map(LagLogicalPort::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LagLogicalPort> findBySwitchIdAndPortNumber(SwitchId switchId, int portNumber) {
        List<? extends LagLogicalPortFrame> lagLogicalPortFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(LagLogicalPortFrame.FRAME_LABEL)
                .has(LagLogicalPortFrame.LOGICAL_PORT_NUMBER_PROPERTY, portNumber)
                .has(LagLogicalPortFrame.SWITCH_ID_PROPERTY,
                        SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(LagLogicalPortFrame.class);

        return lagLogicalPortFrames.isEmpty() ? Optional.empty()
                : Optional.of(lagLogicalPortFrames.get(0)).map(LagLogicalPort::new);
    }

    @Override
    protected LagLogicalPortFrame doAdd(LagLogicalPortData data) {
        LagLogicalPortFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                LagLogicalPortFrame.FRAME_LABEL, LagLogicalPortFrame.class);
        LagLogicalPort.LagLogicalPortCloner.INSTANCE.copyWithoutPhysicalPorts(data, frame);
        frame.setPhysicalPorts(data.getPhysicalPorts());
        return frame;
    }

    @Override
    protected void doRemove(LagLogicalPortFrame frame) {
        frame.getPhysicalPorts().forEach(physicalPort -> {
            if (physicalPort.getData() instanceof PhysicalPortFrame) {
                physicalPortRepository.remove(physicalPort);
            }
        });
        frame.remove();
    }

    @Override
    protected LagLogicalPortData doDetach(LagLogicalPort entity, LagLogicalPortFrame frame) {
        return LagLogicalPort.LagLogicalPortCloner.INSTANCE.deepCopy(frame, entity);
    }
}
