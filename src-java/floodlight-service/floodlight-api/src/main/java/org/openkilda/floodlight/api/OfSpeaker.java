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

package org.openkilda.floodlight.api;

import org.openkilda.floodlight.api.request.OfSpeakerBatchEntry;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface OfSpeaker {
    CompletableFuture<MessageContext> commandsBatch(
            MessageContext context, SwitchId switchId, Collection<OfSpeakerBatchEntry> batch);

    CompletableFuture<MessageContext> installMeter(MessageContext context, SwitchId switchId, MeterConfig meterConfig);

    CompletableFuture<MessageContext> removeMeter(MessageContext context, SwitchId switchId, MeterId meterId);

    // TODO
}
