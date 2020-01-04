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

package org.openkilda.messaging.info.switches;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Collections.unmodifiableList;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwitchRulesResponse extends InfoData {

    @JsonProperty("rule_ids")
    protected List<Long> ruleIds;

    /**
     * Instance constructor.
     *
     * @param ruleIds the list of affected rules
     */
    @JsonCreator
    public SwitchRulesResponse(@JsonProperty("rule_ids") List<Long> ruleIds) {
        this.ruleIds = unmodifiableList(Objects.requireNonNull(ruleIds, "rule_ids must not be null"));
    }

    public List<Long> getRuleIds() {
        return ruleIds;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add("rule_ids", ruleIds)
                .toString();
    }
}
