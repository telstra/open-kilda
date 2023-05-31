/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.model.yflow;

import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.info.event.PathInfoData;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@Builder
public class YFlowPaths {
    @NonNull
    PathInfoData sharedPath;

    @NonNull @Singular
    List<SubFlowPathDto> subFlowPaths;

    /**
     * Builds empty YFlowPaths.
     */
    public static YFlowPaths buildEmpty() {
        return YFlowPaths.builder()
                .sharedPath(new PathInfoData(0, new ArrayList<>()))
                .subFlowPaths(new ArrayList<>())
                .build();
    }

    /**
     * Checks if paths have equal nodes.
     */
    public boolean isSamePath(YFlowPaths otherPaths) {
        if (otherPaths == null) {
            return false;
        }
        return sharedPath.equals(otherPaths.sharedPath) && subFlowPaths.equals(otherPaths.getSubFlowPaths());
    }
}
