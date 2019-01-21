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

package org.openkilda.messaging.nbtopology.request;

import org.openkilda.messaging.model.system.FeatureTogglesDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@SuppressWarnings("squid:MaximumInheritanceDepth")
@Value
@Builder
@JsonInclude(Include.NON_NULL)
public class CreateOrUpdateFeatureTogglesRequest extends FeatureTogglesBaseRequest {

    @JsonProperty("feature_toggles")
    private FeatureTogglesDto featureTogglesDto;

    public CreateOrUpdateFeatureTogglesRequest(@JsonProperty("feature_toggles") FeatureTogglesDto featureTogglesDto) {
        this.featureTogglesDto = featureTogglesDto;
    }
}
