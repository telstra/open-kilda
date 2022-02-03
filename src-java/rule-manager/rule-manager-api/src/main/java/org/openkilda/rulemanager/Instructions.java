/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager;

import org.openkilda.model.MeterId;
import org.openkilda.rulemanager.action.Action;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

@JsonSerialize
@Builder
@Data
@JsonNaming(SnakeCaseStrategy.class)
public class Instructions implements Serializable {

    List<Action> applyActions;
    Set<Action> writeActions;
    MeterId goToMeter;
    OfTable goToTable;
    OfMetadata writeMetadata;

    @JsonCreator
    public Instructions(@JsonProperty("apply_actions") List<Action> applyActions,
                        @JsonProperty("write_actions") Set<Action> writeActions,
                        @JsonProperty("go_to_meter") MeterId goToMeter,
                        @JsonProperty("go_to_table") OfTable goToTable,
                        @JsonProperty("write_metadata") OfMetadata writeMetadata) {
        this.applyActions = applyActions;
        this.writeActions = writeActions;
        this.goToMeter = goToMeter;
        this.goToTable = goToTable;
        this.writeMetadata = writeMetadata;
    }
}
