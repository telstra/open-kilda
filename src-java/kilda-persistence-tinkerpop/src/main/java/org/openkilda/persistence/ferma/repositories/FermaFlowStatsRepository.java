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

package org.openkilda.persistence.ferma.repositories;

import org.openkilda.model.FlowStats;
import org.openkilda.model.FlowStats.FlowStatsCloner;
import org.openkilda.model.FlowStats.FlowStatsData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.FlowStatsFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.FlowStatsRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma (Tinkerpop) implementation of {@link FlowStatsRepository}.
 */
public class FermaFlowStatsRepository
        extends FermaGenericRepository<FlowStats, FlowStatsData, FlowStatsFrame>
        implements FlowStatsRepository {
    public FermaFlowStatsRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Collection<FlowStats> findAll() {
        return framedGraph().traverse(g -> g.V()
                .hasLabel(FlowStatsFrame.FRAME_LABEL))
                .toListExplicit(FlowStatsFrame.class).stream()
                .map(FlowStats::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<FlowStats> findByFlowId(String flowId) {
        List<? extends FlowStatsFrame> flowStatsFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(FlowStatsFrame.FRAME_LABEL)
                .has(FlowStatsFrame.FLOW_ID_PROPERTY, flowId))
                .toListExplicit(FlowStatsFrame.class);
        return flowStatsFrames.isEmpty() ? Optional.empty() : Optional.of(flowStatsFrames.get(0))
                .map(FlowStats::new);
    }

    @Override
    protected FlowStatsFrame doAdd(FlowStatsData data) {
        FlowStatsFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                FlowStatsFrame.FRAME_LABEL, FlowStatsFrame.class);
        FlowStatsCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(FlowStatsFrame frame) {
        frame.remove();
    }

    @Override
    protected FlowStatsData doDetach(FlowStats entity, FlowStatsFrame frame) {
        return FlowStatsCloner.INSTANCE.deepCopy(frame);
    }
}
