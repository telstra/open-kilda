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

import org.openkilda.model.Speaker;
import org.openkilda.model.Speaker.SpeakerData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.SpeakerFrame;
import org.openkilda.persistence.repositories.SpeakerRepository;

import java.util.Optional;

public class FermaSpeakerRepository extends FermaGenericRepository<Speaker, SpeakerData, SpeakerFrame>
        implements SpeakerRepository {
    public FermaSpeakerRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Optional<Speaker> findByName(String name) {
        return SpeakerFrame.load(framedGraph(), name)
                .map(Speaker::new);
    }

    @Override
    protected SpeakerFrame doAdd(SpeakerData data) {
        SpeakerFrame frame = KildaBaseVertexFrame.addNewFramedVertex(
                framedGraph(), SpeakerFrame.FRAME_LABEL, SpeakerFrame.class);
        Speaker.SpeakerCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(SpeakerFrame frame) {
        frame.remove();
    }

    @Override
    protected SpeakerData doDetach(Speaker entity, SpeakerFrame frame) {
        return Speaker.SpeakerCloner.INSTANCE.deepCopy(frame);
    }
}
