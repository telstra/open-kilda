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

package org.openkilda.messaging.info.switches;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonNaming(value = SnakeCaseStrategy.class)
public class GroupInfoEntry implements Serializable {
    private Integer groupId;
    private List<BucketEntry> groupBuckets;

    private List<BucketEntry> missingGroupBuckets;
    private List<BucketEntry> excessGroupBuckets;

    @Data
    @AllArgsConstructor
    @Builder
    @JsonNaming(value = SnakeCaseStrategy.class)
    public static class BucketEntry implements Serializable {
        private Integer port;
        private Integer vlan;
        private Integer vni;
    }
}
