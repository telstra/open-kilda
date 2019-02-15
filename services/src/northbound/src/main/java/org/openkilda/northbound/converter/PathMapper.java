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
import org.openkilda.messaging.info.network.PathsInfoData;
import org.openkilda.northbound.dto.network.PathDto;
import org.openkilda.northbound.dto.network.PathsDto;

import org.mapstruct.Mapper;

import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PathMapper {

    default PathsDto toPathDto(PathsInfoData data) {
        return new PathsDto(data.getTotalBandwidth(),
                data.getPaths().stream().map(this::mapToPath).collect(Collectors.toList()));
    }

    default PathDto mapToPath(Path data) {
        return new PathDto(data.getBandwidth(), data.getLatency(), data.getEdges());
    }
}
