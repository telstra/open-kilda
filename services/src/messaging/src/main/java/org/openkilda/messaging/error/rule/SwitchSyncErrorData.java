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

package org.openkilda.messaging.error.rule;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

/**
 * Defines the payload of a Message representing an error of sync switch rules.
 */
@Value
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwitchSyncErrorData extends ErrorData {

    @JsonIgnore
    private SwitchId switchId;

    @JsonCreator
    public SwitchSyncErrorData(SwitchId switchId,
                               @JsonProperty("error-type") ErrorType errorType,
                               @JsonProperty("error-message") String errorMessage,
                               @JsonProperty("error-description") String errorDescription) {
        super(errorType, errorMessage, errorDescription);
        this.switchId = switchId;
    }
}
