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

import org.openkilda.model.GroupId;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Value
@JsonSerialize
@SuperBuilder
@JsonNaming(SnakeCaseStrategy.class)
public class GroupSpeakerData extends SpeakerData {
    GroupId groupId;
    GroupType type;
    List<Bucket> buckets;

    @JsonCreator
    public GroupSpeakerData(@JsonProperty("uuid") UUID uuid,
                            @JsonProperty("switch_id") SwitchId switchId,
                            @JsonProperty("depends_on") Collection<UUID> dependsOn,
                            @JsonProperty("of_version") OfVersion ofVersion,
                            @JsonProperty("group_id") GroupId groupId,
                            @JsonProperty("type") GroupType type,
                            @JsonProperty("buckets") List<Bucket> buckets) {
        super(uuid, switchId, dependsOn, ofVersion);
        this.groupId = groupId;
        this.type = type;
        this.buckets = buckets;
    }
}
