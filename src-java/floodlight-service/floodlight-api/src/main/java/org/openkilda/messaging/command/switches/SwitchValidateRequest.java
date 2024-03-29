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

package org.openkilda.messaging.command.switches;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.model.ValidationFilter;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class SwitchValidateRequest extends CommandData {

    @JsonProperty("switch_id")
    SwitchId switchId;

    @JsonProperty("perform_sync")
    boolean performSync;

    @Deprecated
    @JsonProperty("remove_excess")
    boolean removeExcess;

    @JsonProperty("validation_filters")
    Set<ValidationFilter> validationFilters;

    @Builder(toBuilder = true)
    @JsonCreator
    public SwitchValidateRequest(@NonNull @JsonProperty("switch_id") SwitchId switchId,
                                 @JsonProperty("perform_sync") boolean performSync,
                                 @JsonProperty("remove_excess") boolean removeExcess,
                                 @JsonProperty("validation_filters") Set<ValidationFilter> validationFilters) {
        this.switchId = switchId;
        this.performSync = performSync;
        this.removeExcess = removeExcess;
        this.validationFilters = validationFilters;
    }
}

