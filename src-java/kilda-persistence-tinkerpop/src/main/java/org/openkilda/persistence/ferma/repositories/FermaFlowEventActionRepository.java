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

import org.openkilda.model.history.FlowEventAction;
import org.openkilda.model.history.FlowEventAction.FlowEventActionCloner;
import org.openkilda.model.history.FlowEventAction.FlowEventActionData;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FlowEventActionFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.history.FlowEventActionRepository;
import org.openkilda.persistence.tx.TransactionManager;

/**
 * Ferma (Tinkerpop) implementation of {@link FlowEventActionRepository}.
 */
public class FermaFlowEventActionRepository
        extends FermaGenericRepository<FlowEventAction, FlowEventActionData, FlowEventActionFrame>
        implements FlowEventActionRepository {
    FermaFlowEventActionRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    protected FlowEventActionFrame doAdd(FlowEventActionData data) {
        FlowEventActionFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                FlowEventActionFrame.FRAME_LABEL, FlowEventActionFrame.class);
        FlowEventActionCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(FlowEventActionFrame frame) {
        frame.remove();
    }

    @Override
    protected FlowEventActionData doDetach(FlowEventAction entity, FlowEventActionFrame frame) {
        return FlowEventActionCloner.INSTANCE.deepCopy(frame);
    }
}
