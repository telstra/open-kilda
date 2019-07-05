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

package org.openkilda.messaging.info.switches;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
@Builder
public class RulesSyncEntry implements Serializable {

    @JsonProperty("missing")
    List<Long> missing;

    @JsonProperty("proper")
    List<Long> proper;

    @JsonProperty("excess")
    List<Long> excess;

    @JsonProperty("installed")
    List<Long> installed;

    @JsonProperty("removed")
    List<Long> removed;

    @JsonCreator
    public RulesSyncEntry(@JsonProperty("missing") List<Long> missing,
                          @JsonProperty("proper") List<Long> proper,
                          @JsonProperty("excess") List<Long> excess,
                          @JsonProperty("installed") List<Long> installed,
                          @JsonProperty("removed") List<Long> removed) {
        this.missing = missing;
        this.proper = proper;
        this.excess = excess;
        this.installed = installed;
        this.removed = removed;
    }
}
