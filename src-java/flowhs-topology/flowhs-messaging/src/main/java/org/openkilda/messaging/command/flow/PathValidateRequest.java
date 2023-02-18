/* Copyright 2023 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.nbtopology.annotations.ReadRequest;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.payload.network.PathValidationDto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Request to validate that the given path is possible to create with the given constraints and resources availability.
 */
@Value
@ReadRequest
@EqualsAndHashCode(callSuper = true)
public class PathValidateRequest extends BaseRequest {

    @JsonProperty("path")
    PathValidationDto pathValidationDto;

    @JsonCreator
    public PathValidateRequest(@JsonProperty("path") PathValidationDto pathValidationDtoDto) {
        this.pathValidationDto = pathValidationDtoDto;
    }
}
