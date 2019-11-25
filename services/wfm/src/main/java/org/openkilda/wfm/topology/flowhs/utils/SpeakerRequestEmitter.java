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

import static java.util.Collections.unmodifiableMap;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class SpeakerRequestEmitter {
    protected final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    /**
     * Emit series of speaker requests produced from proposed series of request factories.
     */
    public Map<UUID, FlowSegmentRequestFactory> emitBatch(
            FlowGenericCarrier carrier, Collection<FlowSegmentRequestFactory> factories) {
        Map<UUID, FlowSegmentRequestFactory> requestsStorage = new HashMap<>();

        for (FlowSegmentRequestFactory factory : factories) {
            FlowSegmentRequest request = makeRequest(factory);
            // TODO ensure no conflicts
            requestsStorage.put(request.getCommandId(), factory);
            carrier.sendSpeakerRequest(request);
        }

        return unmodifiableMap(requestsStorage);
    }

    protected abstract FlowSegmentRequest makeRequest(FlowSegmentRequestFactory factory);
}
