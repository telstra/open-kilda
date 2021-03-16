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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.Speaker.SpeakerData;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;

import java.util.Iterator;
import java.util.Optional;

public abstract class SpeakerFrame extends KildaBaseVertexFrame implements SpeakerData {
    public static final String FRAME_LABEL = "speaker";

    public static final String NAME_PROPERTY = "name";

    @Override
    @Property(NAME_PROPERTY)
    public abstract String getName();

    @Override
    @Property(NAME_PROPERTY)
    public abstract void setName(String name);

    public static Optional<SpeakerFrame> load(FramedGraph graph, String name) {
        Iterator<? extends SpeakerFrame> iterator = graph.traverse(g -> g.V()
                .hasLabel(SpeakerFrame.FRAME_LABEL)
                .has(SpeakerFrame.NAME_PROPERTY, name))
                .frameExplicit(SpeakerFrame.class);
        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        }
        return Optional.empty();
    }
}
