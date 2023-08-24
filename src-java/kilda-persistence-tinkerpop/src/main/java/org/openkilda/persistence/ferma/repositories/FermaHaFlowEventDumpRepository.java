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

import org.openkilda.model.history.HaFlowEventDump;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpCloner;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.HaFlowEventDumpFrame;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.repositories.history.HaFlowEventDumpRepository;

public class FermaHaFlowEventDumpRepository
        extends FermaGenericRepository<HaFlowEventDump, HaFlowEventDumpData, HaFlowEventDumpFrame>
        implements HaFlowEventDumpRepository {
    FermaHaFlowEventDumpRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    protected HaFlowEventDumpFrame doAdd(HaFlowEventDumpData data) {
        HaFlowEventDumpFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                HaFlowEventDumpFrame.FRAME_LABEL,
                HaFlowEventDumpFrame.class);
        HaFlowEventDumpCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(HaFlowEventDumpFrame frame) {
        frame.remove();
    }

    @Override
    protected HaFlowEventDumpData doDetach(HaFlowEventDump entity, HaFlowEventDumpFrame frame) {
        return HaFlowEventDumpCloner.INSTANCE.deepCopy(frame);
    }
}
