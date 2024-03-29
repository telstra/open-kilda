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

package org.openkilda.northbound.dto.v1.switches;

import org.openkilda.northbound.dto.HexView;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RulesValidationDto implements HexView {

    @JsonProperty("missing")
    protected List<Long> missing;

    @JsonProperty("misconfigured")
    protected List<Long> misconfigured;

    @JsonProperty("proper")
    protected List<Long> proper;

    @JsonProperty("excess")
    protected List<Long> excess;

    @JsonGetter("missing-hex")
    public List<String> getMissingHex() {
        return toHex(missing);
    }

    @JsonGetter("misconfigured-hex")
    public List<String> getMisconfiguredHex() {
        return toHex(misconfigured);
    }

    @JsonGetter("proper-hex")
    public List<String> getProperHex() {
        return toHex(proper);
    }

    @JsonGetter("excess-hex")
    public List<String> getExcessHex() {
        return toHex(excess);
    }

    public static RulesValidationDto empty() {
        return new RulesValidationDto(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
