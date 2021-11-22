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

import org.openkilda.floodlight.api.request.command.MeterRemoveCommand;
import org.openkilda.floodlight.api.request.rulemanager.SpeakerCommandsBatchRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.GroupSpeakerCommandData;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.SpeakerCommandData;

import java.util.List;
import java.util.UUID;

public class OfSpeakerRemoveCommandAdapter extends OfSpeakerCommandAdapterBase {
    public static SpeakerCommandsBatchRequest adaptBatch(
            MessageContext messageContext, UUID commandId, List<SpeakerCommandData> speakerEntries) {
        return OfSpeakerCommandAdapterBase.newBatchRequest(
                new OfSpeakerRemoveCommandAdapter(messageContext), commandId, speakerEntries);
    }

    protected OfSpeakerRemoveCommandAdapter(MessageContext messageContext) {
        super(messageContext);
    }

    @Override
    public void handle(FlowSpeakerCommandData flow) {
        // TODO
    }

    @Override
    public void handle(MeterSpeakerCommandData meter) {
        entries.add(new MeterRemoveCommand(getMessageContext(), meter));
    }

    @Override
    public void handle(GroupSpeakerCommandData group) {
        // TODO
    }
}
