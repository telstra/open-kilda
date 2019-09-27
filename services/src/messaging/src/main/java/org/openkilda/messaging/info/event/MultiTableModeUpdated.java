/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.info.event;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MultiTableModeUpdated  extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    private PathNode source;
    private PathNode destination;

    @Builder(toBuilder = true)
    @JsonCreator
    public MultiTableModeUpdated(@JsonProperty("source") PathNode source,
                                 @JsonProperty("destination") PathNode destination) {
        this.source = source;
        this.destination = destination;
    }
}
