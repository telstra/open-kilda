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

import org.openkilda.model.history.FlowEventDump;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpCloner;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpData;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FlowEventDumpFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.history.FlowEventDumpRepository;
import org.openkilda.persistence.tx.TransactionManager;

/**
 * Ferma (Tinkerpop) implementation of {@link FlowEventDumpRepository}.
 */
public class FermaFlowEventDumpRepository
        extends FermaGenericRepository<FlowEventDump, FlowEventDumpData, FlowEventDumpFrame>
        implements FlowEventDumpRepository {
    FermaFlowEventDumpRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    protected FlowEventDumpFrame doAdd(FlowEventDumpData data) {
        FlowEventDumpFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                FlowEventDumpFrame.FRAME_LABEL, FlowEventDumpFrame.class);
        FlowEventDumpCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(FlowEventDumpFrame frame) {
        frame.remove();
    }

    @Override
    protected FlowEventDumpData doDetach(FlowEventDump entity, FlowEventDumpFrame frame) {
        return FlowEventDumpCloner.INSTANCE.deepCopy(frame);
    }
}
