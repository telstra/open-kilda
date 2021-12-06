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

package org.openkilda.floodlight.api.request.rulemanager;

import org.openkilda.floodlight.api.BatchCommandProcessor;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import lombok.NonNull;

import java.util.List;
import java.util.UUID;

public class DeleteSpeakerCommandsRequest extends BaseSpeakerCommandsRequest {

    public DeleteSpeakerCommandsRequest(MessageContext messageContext,
                                         @NonNull SwitchId switchId,
                                         @NonNull UUID commandId,
                                         List<OfCommand> commands) {
        super(messageContext, switchId, commandId, commands);
    }

    public void process(BatchCommandProcessor processor, String key) {
        processor.processBatchDelete(this, key);
    }
}
