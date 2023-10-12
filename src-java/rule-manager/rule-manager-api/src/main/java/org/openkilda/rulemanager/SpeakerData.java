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

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@JsonSerialize
@Getter
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
@EqualsAndHashCode(of = {"switchId", "ofVersion"})
@ToString
public abstract class SpeakerData implements Serializable {

    @Builder.Default
    protected UUID uuid = UUID.randomUUID();
    protected SwitchId switchId;
    @Builder.Default
    protected Collection<UUID> dependsOn = new ArrayList<>();
    protected OfVersion ofVersion;
}
