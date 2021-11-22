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

package org.openkilda.floodlight.api.request;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.SpeakerCommandData;

import lombok.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// TODO Need better name, because of "binding" to rules-manager-api objects
public abstract class SpeakerServiceRequest extends SpeakerRequest implements OfSpeakerBatchEntry {
    private final SpeakerCommandData payload;

    public SpeakerServiceRequest(
            MessageContext messageContext, @NonNull SwitchId switchId, @NonNull UUID commandId,
            SpeakerCommandData payload) {
        super(messageContext, switchId, commandId);
        this.payload = payload;
    }

    @Override
    public Set<UUID> dependencies() {
        // TODO: Do we need write protection? Perhaps should be just "return dependsOd"
        return new HashSet<>(payload.getDependsOnCommands());
    }
}
