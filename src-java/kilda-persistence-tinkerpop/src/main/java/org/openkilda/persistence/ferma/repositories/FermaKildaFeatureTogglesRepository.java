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

import org.openkilda.model.KildaFeatureToggles;
import org.openkilda.model.KildaFeatureToggles.KildaFeatureTogglesData;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.KildaFeatureTogglesFrame;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;

import java.util.List;
import java.util.Optional;

/**
 * Ferma (Tinkerpop) implementation of {@link KildaFeatureTogglesRepository}.
 */
public class FermaKildaFeatureTogglesRepository
        extends FermaGenericRepository<KildaFeatureToggles, KildaFeatureTogglesData, KildaFeatureTogglesFrame>
        implements KildaFeatureTogglesRepository {
    public FermaKildaFeatureTogglesRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    public Optional<KildaFeatureToggles> find() {
        List<? extends KildaFeatureTogglesFrame> featureTogglesFrames = framedGraph().traverse(g -> g.V()
                .hasLabel(KildaFeatureTogglesFrame.FRAME_LABEL))
                .toListExplicit(KildaFeatureTogglesFrame.class);
        return featureTogglesFrames.isEmpty() ? Optional.empty() : Optional.of(featureTogglesFrames.get(0))
                .map(KildaFeatureToggles::new);
    }

    @Override
    public KildaFeatureToggles getOrDefault() {
        KildaFeatureToggles result = new KildaFeatureToggles(find().orElse(KildaFeatureToggles.DEFAULTS));
        KildaFeatureToggles.FeatureTogglesCloner.INSTANCE.replaceNullProperties(KildaFeatureToggles.DEFAULTS, result);
        return result;
    }

    @Override
    protected KildaFeatureTogglesFrame doAdd(KildaFeatureTogglesData data) {
        KildaFeatureTogglesFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                KildaFeatureTogglesFrame.FRAME_LABEL, KildaFeatureTogglesFrame.class);
        frame.setProperty(KildaFeatureTogglesFrame.UNIQUE_PROPERTY, KildaFeatureTogglesFrame.FRAME_LABEL);
        KildaFeatureToggles.FeatureTogglesCloner.INSTANCE.copyNonNull(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(KildaFeatureTogglesFrame frame) {
        frame.remove();
    }

    @Override
    protected KildaFeatureTogglesData doDetach(KildaFeatureToggles entity, KildaFeatureTogglesFrame frame) {
        return KildaFeatureToggles.FeatureTogglesCloner.INSTANCE.deepCopy(frame);
    }
}
