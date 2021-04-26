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
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnect;
import org.openkilda.model.SwitchConnect.SwitchConnectCloner;
import org.openkilda.model.SwitchConnect.SwitchConnectData;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.KildaBaseEdgeFrame;
import org.openkilda.persistence.ferma.frames.SpeakerFrame;
import org.openkilda.persistence.ferma.frames.SwitchConnectFrame;
import org.openkilda.persistence.ferma.frames.SwitchFrame;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.repositories.SwitchConnectRepository;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FermaSwitchConnectRepository
        extends FermaGenericRepository<SwitchConnect, SwitchConnectData, SwitchConnectFrame>
        implements SwitchConnectRepository {

    public FermaSwitchConnectRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    public List<SwitchConnect> findBySwitchId(@NonNull SwitchId switchId) {
        return SwitchFrame.load(
                framedGraph(), SwitchIdConverter.INSTANCE.toGraphProperty(switchId))
                .map(sw -> sw.traverse(g -> g.outE(SwitchConnectFrame.FRAME_LABEL))
                        .toListExplicit(SwitchConnectFrame.class)
                        .stream()
                        .map(SwitchConnect::new)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    protected SwitchConnectFrame doAdd(SwitchConnectData data) {
        Switch owner = data.getOwner();
        if (owner == null || owner.getSwitchId() == null) {
            throw new IllegalArgumentException("Owner or owner switchId field is null");
        }
        SwitchFrame ownerFrame = SwitchFrame.load(
                framedGraph(), SwitchIdConverter.INSTANCE.toGraphProperty(owner.getSwitchId()))
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Unable to locate the switch %s", owner.getSwitchId())));
        Speaker speaker = data.getSpeaker();
        if (speaker == null || speaker.getName() == null) {
            throw new IllegalArgumentException("Speaker or speaker name is null");
        }
        SpeakerFrame speakerFrame = SpeakerFrame.load(framedGraph(), speaker.getName())
                .orElseThrow(() -> new IllegalArgumentException("Unable to locate speaker " + speaker.getName()));

        SwitchConnectFrame frame = KildaBaseEdgeFrame.addNewFramedEdge(
                framedGraph(), ownerFrame, speakerFrame, SwitchConnectFrame.FRAME_LABEL, SwitchConnectFrame.class);
        SwitchConnectCloner.INSTANCE.copyWithoutRelations(data, frame);
        return frame;
    }

    @Override
    protected void doRemove(SwitchConnectFrame frame) {
        frame.remove();
    }

    @Override
    protected SwitchConnectData doDetach(SwitchConnect entity, SwitchConnectFrame frame) {
        return SwitchConnectCloner.INSTANCE.deepCopy(frame);
    }
}
