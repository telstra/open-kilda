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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.info.network.Path;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload.FlowProtectedPathsPayload;
import org.openkilda.messaging.payload.network.PathDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PathMapper {

    /**
     * Maps a Path object to a more user-friendly representation.
     * @param data Path object
     * @return a Path representation that is supposed to be used as JSON payload in NB API
     */
    default PathDto mapToPath(Path data) {
        PathDto protectedPath = data.getProtectedPath() == null ? null : mapToPath(data.getProtectedPath());

        if (data.getProtectedPath() != null && data.getProtectedPath().getProtectedPath() != null) {
            throw new IllegalStateException("A protected path to some path cannot have its own protected path.");
        }

        return new PathDto(data.getBandwidth(), data.getLatency(), data.getNodes(), data.getIsBackupPath(),
                protectedPath);
    }

    GroupFlowPathPayload mapGroupFlowPathPayload(FlowPathDto data);

    FlowProtectedPathsPayload mapFlowProtectedPathPayload(FlowPathDto.FlowProtectedPathDto data);
}
