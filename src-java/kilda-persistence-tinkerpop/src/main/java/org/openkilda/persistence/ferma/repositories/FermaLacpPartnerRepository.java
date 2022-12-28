/* Copyright 2022 Telstra Open Source
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

import org.openkilda.model.LacpPartner;
import org.openkilda.model.LacpPartner.LacpPartnerData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.ferma.frames.KildaBaseVertexFrame;
import org.openkilda.persistence.ferma.frames.LacpPartnerFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.LacpPartnerRepository;
import org.openkilda.persistence.repositories.PortRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ferma implementation of {@link PortRepository}.
 */
@Slf4j
public class FermaLacpPartnerRepository extends FermaGenericRepository<LacpPartner, LacpPartnerData, LacpPartnerFrame>
        implements LacpPartnerRepository {
    public FermaLacpPartnerRepository(FermaPersistentImplementation implementation) {
        super(implementation);
    }

    @Override
    protected LacpPartnerFrame doAdd(LacpPartnerData data) {
        LacpPartnerFrame frame = KildaBaseVertexFrame.addNewFramedVertex(framedGraph(),
                LacpPartnerFrame.FRAME_LABEL, LacpPartnerFrame.class);

        LacpPartner.LacpPartnerCloner.INSTANCE.copy(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(LacpPartnerFrame frame) {
        frame.remove();
    }

    @Override
    protected LacpPartnerData doDetach(LacpPartner entity, LacpPartnerFrame frame) {
        return LacpPartner.LacpPartnerCloner.INSTANCE.deepCopy(frame);
    }

    @Override
    public Collection<LacpPartner> findAll() {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(LacpPartnerFrame.FRAME_LABEL))
                .toListExplicit(LacpPartnerFrame.class).stream()
                .map(LacpPartner::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<LacpPartner> findBySwitchId(SwitchId switchId) {
        return framedGraph().traverse(g -> g.V()
                        .hasLabel(LacpPartnerFrame.FRAME_LABEL)
                        .has(LacpPartnerFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId)))
                .toListExplicit(LacpPartnerFrame.class).stream()
                .map(LacpPartner::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LacpPartner> findBySwitchIdAndLogicalPortNumber(SwitchId switchId, int logicalPortNumber) {
        List<? extends LacpPartnerFrame> lacpPartnerFrames = framedGraph().traverse(g -> g.V()
                        .hasLabel(LacpPartnerFrame.FRAME_LABEL)
                        .has(LacpPartnerFrame.SWITCH_ID_PROPERTY, SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                        .has(LacpPartnerFrame.LOGICAL_PORT_NUMBER_PROPERTY, logicalPortNumber))
                .toListExplicit(LacpPartnerFrame.class);
        return lacpPartnerFrames.isEmpty() ? Optional.empty() : Optional.of(lacpPartnerFrames.get(0))
                .map(LacpPartner::new);
    }
}
