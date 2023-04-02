/* Copyright 2020 Telstra Open Source
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

package org.openkilda.pce.model;

import org.openkilda.pce.finder.FailReason;
import org.openkilda.pce.finder.FailReasonType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@Setter
public class FindOneDirectionPathResult {
    public FindOneDirectionPathResult(List<Edge> foundPath, boolean backUpPathComputationWayUsed) {
        this(foundPath, backUpPathComputationWayUsed, null);
    }

    public FindOneDirectionPathResult(List<Edge> foundPath, Map<FailReasonType, FailReason> pathNotFoundReasons) {
        this(foundPath, false, pathNotFoundReasons);
    }

    List<Edge> foundPath;
    boolean backUpPathComputationWayUsed;
    Map<FailReasonType, FailReason> pathNotFoundReasons;
}
