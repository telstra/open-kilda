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

package org.openkilda.northbound.dto.v1.switches;

import org.openkilda.northbound.dto.HexView;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RulesValidationResult implements HexView {

    @JsonProperty("missing_rules")
    private List<Long> missingRules;

    @JsonProperty("proper_rules")
    private List<Long> properRules;

    @JsonProperty("excess_rules")
    private List<Long> excessRules;


    @JsonGetter("missing-rules-hex")
    public List<String> getMissingRulesHex() {
        return toHex(missingRules);
    }

    @JsonGetter("proper-rules-hex")
    public List<String> getProperRulesHex() {
        return toHex(properRules);
    }

    @JsonGetter("excess-rules-hex")
    public List<String> getExcessHex() {
        return toHex(excessRules);
    }
}
