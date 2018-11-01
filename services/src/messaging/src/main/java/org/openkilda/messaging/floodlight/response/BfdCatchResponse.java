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

package org.openkilda.messaging.floodlight.response;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.NoviBfdCatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class BfdCatchResponse extends InfoData {
    @JsonProperty("bfd-catch")
    private NoviBfdCatch bfdCatch;

    @JsonProperty("error-code")
    private NoviBfdCatch.Errors errorCode;

    @Builder
    @JsonCreator
    public BfdCatchResponse(
            @JsonProperty("bfd-catch") NoviBfdCatch bfdCatch,
            @JsonProperty("error-code") NoviBfdCatch.Errors errorCode) {
        this.bfdCatch = bfdCatch;
        this.errorCode = errorCode;
    }
}
