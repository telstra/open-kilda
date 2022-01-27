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
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.LagLogicalPortFrame;
import org.openkilda.persistence.ferma.frames.PhysicalPortFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;

import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;

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

    public FermaLagLogicalPortRepository(
            FermaPersistentImplementation implementation, PhysicalPortRepository physicalPortRepository) {
        super(implementation);
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
    public Optional<Integer> findUnassignedPortInRange(SwitchId switchId, int portFirst, int portLast) {
        String switchIdAsStr = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);

        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                        .hasLabel(LagLogicalPortFrame.FRAME_LABEL)
                        .has(LagLogicalPortFrame.SWITCH_ID_PROPERTY, switchIdAsStr)
                        .has(LagLogicalPortFrame.LOGICAL_PORT_NUMBER_PROPERTY, P.gte(portFirst))
                        .has(LagLogicalPortFrame.LOGICAL_PORT_NUMBER_PROPERTY, P.lt(portLast))
                        .values(LagLogicalPortFrame.LOGICAL_PORT_NUMBER_PROPERTY)
                        .order().math("_ + 1").as("a")
                        .where(__.not(__.V().hasLabel(LagLogicalPortFrame.FRAME_LABEL)
                                .has(LagLogicalPortFrame.SWITCH_ID_PROPERTY, switchIdAsStr)
                                .values(LagLogicalPortFrame.LOGICAL_PORT_NUMBER_PROPERTY)
                                .where(P.eq("a"))))
                        .select("a")
                        .limit(1))
                .getRawTraversal()) {
            if (traversal.hasNext()) {
                return traversal.tryNext()
                        .map(l -> ((Double) l).intValue());
            }
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }

        // If there is no one record for specific switch exists
        try (GraphTraversal<?, ?> traversal = framedGraph().traverse(g -> g.V()
                        .hasLabel(LagLogicalPortFrame.FRAME_LABEL)
                        .has(LagLogicalPortFrame.SWITCH_ID_PROPERTY, switchIdAsStr)
                        .has(LagLogicalPortFrame.LOGICAL_PORT_NUMBER_PROPERTY, portFirst))
                .getRawTraversal()) {
            if (! traversal.hasNext()) {
                return Optional.of(portFirst);
            }
        } catch (Exception e) {
            throw new PersistenceException("Failed to traverse", e);
        }

        return Optional.empty();
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
