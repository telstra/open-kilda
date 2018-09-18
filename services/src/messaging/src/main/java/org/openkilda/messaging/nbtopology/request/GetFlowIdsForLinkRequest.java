/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.nbtopology.request;

import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.annotations.ReadRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@SuppressWarnings("squid:MaximumInheritanceDepth")
@ReadRequest
@Getter
public class GetFlowIdsForLinkRequest extends FlowsBaseRequest {

    @JsonProperty("source")
    private NetworkEndpointMask source;

    @JsonProperty("destination")
    private NetworkEndpointMask destination;

    @JsonProperty("correlation_id")
    protected String correlationId;

    public GetFlowIdsForLinkRequest(@JsonProperty("source") NetworkEndpointMask source,
                                    @JsonProperty("destination") NetworkEndpointMask destination,
                                    @JsonProperty("correlation_id") String correlationId) {
        this.source = source;
        this.destination = destination;
        this.correlationId = correlationId;
    }
}
