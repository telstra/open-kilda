/* Copyright 2020 Telstra Open Source
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

package org.openkilda.messaging.info.reroute.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = FlowInProgressError.class,
                name = "org.openkilda.messaging.info.reroute.error.FlowInProgressError"),
        @Type(value = RerouteInProgressError.class,
                name = "org.openkilda.messaging.info.reroute.error.RerouteInProgressError"),
        @Type(value = SpeakerRequestError.class,
                name = "org.openkilda.messaging.info.reroute.error.RuleFailedError"),
        @Type(value = NoPathFoundError.class,
                name = "org.openkilda.messaging.info.reroute.error.NoPathFoundError")
})
public class RerouteError implements Serializable {

    private String message;

    @JsonCreator
    public RerouteError(@JsonProperty("message") String message) {
        this.message = message;
    }
}
