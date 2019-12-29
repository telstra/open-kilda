/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class SpeakerRequestEmitter {
    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    /**
     * Emit series of speaker requests produced from proposed series of request factories.
     */
    public HashMap<UUID, FlowSegmentRequestFactory> emitBatch(
            FlowGenericCarrier carrier, Collection<FlowSegmentRequestFactory> factories) {
        final HashMap<UUID, FlowSegmentRequestFactory> requestsStorage = new HashMap<>();
        emitBatch(carrier, factories, requestsStorage);
        return requestsStorage;
    }

    /**
     * Emit series of speaker requests produced from proposed series of request factories.
     */
    public void emitBatch(
            FlowGenericCarrier carrier, Collection<FlowSegmentRequestFactory> factories,
            Map<UUID, FlowSegmentRequestFactory> requestsStorage) {

        for (FlowSegmentRequestFactory factory : factories) {
            Optional<? extends FlowSegmentRequest> potentialRequest = makeRequest(factory);
            if (!potentialRequest.isPresent()) {
                continue;
            }

            FlowSegmentRequest request = potentialRequest.get();
            requestsStorage.put(request.getCommandId(), factory);
            carrier.sendSpeakerRequest(request);
        }
    }

    /**
     * Emit series of speaker requests reusing already stored commandId.
     */
    public Set<UUID> emitBatchKeepingCommandId(
            FlowGenericCarrier carrier, Map<UUID, FlowSegmentRequestFactory> requestsStorage) {
        for (Map.Entry<UUID, FlowSegmentRequestFactory> entry : requestsStorage.entrySet()) {
            Optional<? extends FlowSegmentRequest> potentialRequest = makeRequest(entry.getValue(), entry.getKey());
            if (! potentialRequest.isPresent()) {
                continue;
            }

            carrier.sendSpeakerRequest(potentialRequest.get());
        }

        return new HashSet<>(requestsStorage.keySet());
    }

    private Optional<? extends FlowSegmentRequest> makeRequest(FlowSegmentRequestFactory factory)  {
        return makeRequest(factory, commandIdGenerator.generate());
    }

    protected abstract Optional<? extends FlowSegmentRequest> makeRequest(
            FlowSegmentRequestFactory factory, UUID commandId);
}
