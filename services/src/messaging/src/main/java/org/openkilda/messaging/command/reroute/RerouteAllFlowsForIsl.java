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

package org.openkilda.messaging.command.reroute;

import org.openkilda.messaging.info.event.PathNode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerouteAllFlowsForIsl extends RerouteFlows {

    @JsonProperty("source")
    private PathNode source;

    @JsonProperty("destination")
    private PathNode destination;

    @JsonCreator
    public RerouteAllFlowsForIsl(@NonNull @JsonProperty("source") PathNode source,
                                 @NonNull @JsonProperty("destination") PathNode destination,
                                 @NonNull @JsonProperty("reason") String reason) {
        super(reason);
        this.source = source;
        this.destination = destination;
    }

    public PathNode getSource() {
        return source;
    }

    public PathNode getDestination() {
        return destination;
    }
}
