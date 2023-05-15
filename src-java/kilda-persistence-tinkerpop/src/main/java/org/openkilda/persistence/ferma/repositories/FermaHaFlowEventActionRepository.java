/* Copyright 2023 Telstra Open Source
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

import org.openkilda.model.history.HaFlowEventAction;
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionCloner;
import org.openkilda.model.history.HaFlowEventAction.HaFlowEventActionData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.HaFlowEventActionFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.history.HaFlowEventActionRepository;

public class FermaHaFlowEventActionRepository
        extends FermaGenericRepository<HaFlowEventAction, HaFlowEventActionData, HaFlowEventActionFrame>
        implements HaFlowEventActionRepository {
    FermaHaFlowEventActionRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    protected HaFlowEventActionFrame doAdd(HaFlowEventActionData data) {
        HaFlowEventActionFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                HaFlowEventActionFrame.FRAME_LABEL,
                HaFlowEventActionFrame.class);
        HaFlowEventActionCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(HaFlowEventActionFrame frame) {
        frame.remove();
    }

    @Override
    protected HaFlowEventActionData doDetach(HaFlowEventAction entity, HaFlowEventActionFrame frame) {
        return HaFlowEventActionCloner.INSTANCE.deepCopy(frame);
    }
}
