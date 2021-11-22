/*
 * Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager.arapter;

import org.openkilda.floodlight.api.request.OfSpeakerBatchEntry;
import org.openkilda.floodlight.api.request.OfSpeakerCommand;
import org.openkilda.floodlight.api.request.rulemanager.SpeakerCommandsBatchRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.OfSpeakerEntryHandler;
import org.openkilda.rulemanager.SpeakerCommandData;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

abstract class OfSpeakerCommandAdapterBase implements OfSpeakerEntryHandler {
    static SpeakerCommandsBatchRequest newBatchRequest(
            OfSpeakerCommandAdapterBase adapter, UUID commandId, List<SpeakerCommandData> speakerEntries) {
        for (SpeakerCommandData entry : speakerEntries) {
            entry.handle(adapter);
        }

        return new SpeakerCommandsBatchRequest(
                adapter.getMessageContext(), adapter.getSwitchId(), commandId, adapter.getEntries());
    }


    @Getter
    protected final Collection<OfSpeakerBatchEntry> entries = new ArrayList<>();

    @Getter
    protected final MessageContext messageContext;

    protected OfSpeakerCommandAdapterBase(MessageContext messageContext) {
        this.messageContext = messageContext;
    }

    public SwitchId getSwitchId() {
        Set<SwitchId> targets = entries.stream()
                .map(OfSpeakerCommand::getSwitchId)
                .collect(Collectors.toSet());
        if (1 < targets.size()) {
            throw new IllegalArgumentException(String.format(
                    "OF speaker batch targets more than 1 switch: %s",
                    targets.stream().
                            map(SwitchId::toString)
                            .collect(Collectors.joining(", "))));
        }

        if (targets.isEmpty()) {
            throw new IllegalArgumentException("There is no OF speaker requests");
        }
        return targets.iterator().next();
    }
}
