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

package org.openkilda.messaging.nbtopology.response;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.system.FeatureTogglesDto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@ToString
@EqualsAndHashCode(callSuper = false)
public class FeatureTogglesResponse extends InfoData {

    @JsonProperty("feature_toggles")
    private FeatureTogglesDto featureTogglesDto;

    @JsonCreator
    public FeatureTogglesResponse(@JsonProperty("feature_toggles") FeatureTogglesDto featureTogglesDto) {
        this.featureTogglesDto = featureTogglesDto;
    }
}
