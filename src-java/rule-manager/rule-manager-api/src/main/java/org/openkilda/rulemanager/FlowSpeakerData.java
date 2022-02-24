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
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.rulemanager.match.FieldMatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Value
@SuperBuilder(toBuilder = true)
@JsonNaming(SnakeCaseStrategy.class)
@ToString(callSuper = true)
public class FlowSpeakerData extends SpeakerData {

    CookieBase cookie;
    OfTable table;
    int priority;
    Set<FieldMatch> match;
    Instructions instructions;
    Set<OfFlowFlag> flags;

    @EqualsAndHashCode.Exclude
    long durationSeconds;
    @EqualsAndHashCode.Exclude
    long durationNanoSeconds;
    @EqualsAndHashCode.Exclude
    long packetCount;
    @EqualsAndHashCode.Exclude
    long idleTimeout;
    @EqualsAndHashCode.Exclude
    long hardTimeout;
    @EqualsAndHashCode.Exclude
    long byteCount;

    @JsonCreator
    public FlowSpeakerData(@JsonProperty("uuid") UUID uuid,
                           @JsonProperty("switch_id") SwitchId switchId,
                           @JsonProperty("depends_on") Collection<UUID> dependsOn,
                           @JsonProperty("of_version") OfVersion ofVersion,
                           @JsonProperty("cookie") long cookie,
                           @JsonProperty("table") OfTable table,
                           @JsonProperty("priority") int priority,
                           @JsonProperty("match") Set<FieldMatch> match,
                           @JsonProperty("instructions") Instructions instructions,
                           @JsonProperty("flags") Set<OfFlowFlag> flags,
                           @JsonProperty("duration_seconds") long durationSeconds,
                           @JsonProperty("duration_nano_seconds") long durationNanoSeconds,
                           @JsonProperty("packet_count") long packetCount,
                           @JsonProperty("idle_timeout") long idleTimeout,
                           @JsonProperty("hard_timeout") long hardTimeout,
                           @JsonProperty("byte_count") long byteCount) {
        super(uuid, switchId, dependsOn, ofVersion);
        this.cookie = new Cookie(cookie);
        this.table = table;
        this.priority = priority;
        this.match = match;
        this.instructions = instructions;
        this.flags = flags;
        this.durationSeconds = durationSeconds;
        this.durationNanoSeconds = durationNanoSeconds;
        this.packetCount = packetCount;
        this.idleTimeout = idleTimeout;
        this.hardTimeout = hardTimeout;
        this.byteCount = byteCount;
    }

    public abstract static class FlowSpeakerDataBuilder<C extends FlowSpeakerData,
            B extends FlowSpeakerDataBuilder<C, B>>  extends SpeakerDataBuilder<C, B> {
        private Set<FieldMatch> match = new HashSet<>();
        private Set<OfFlowFlag> flags = new HashSet<>();

        /**
         * If two FieldMatches have same field type for matching only one of them will be added into match set.
         */
        public B match(Set<FieldMatch> match) {
            Map<Field, FieldMatch> map = new HashMap<>();
            for (FieldMatch fieldMatch : match) {
                map.put(fieldMatch.getField(), fieldMatch);
            }
            this.match = new HashSet<>(map.values());
            return self();
        }

        public B flags(Set<OfFlowFlag> flags) {
            this.flags.addAll(flags);
            return self();
        }
    }
}
