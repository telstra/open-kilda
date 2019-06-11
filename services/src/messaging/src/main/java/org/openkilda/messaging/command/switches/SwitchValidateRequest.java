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
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
public class SwitchValidateRequest extends CommandData {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("process_meters")
    private boolean processMeters;

    @JsonProperty("perform_sync")
    private boolean performSync;

    @JsonProperty("remove_excess")
    private boolean removeExcess;

    @Builder
    @JsonCreator
    public SwitchValidateRequest(@NonNull @JsonProperty("switch_id") SwitchId switchId,
                                 @JsonProperty("process_meters") boolean processMeters,
                                 @JsonProperty("perform_sync") boolean performSync,
                                 @JsonProperty("remove_excess") boolean removeExcess) {
        this.switchId = switchId;
        this.processMeters = processMeters;
        this.performSync = performSync;
        this.removeExcess = removeExcess;
    }
}

