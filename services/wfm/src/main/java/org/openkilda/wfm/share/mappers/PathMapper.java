/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convert {@link org.openkilda.pce.Path} to {@link org.openkilda.messaging.info.network.Path}.
 */
@Mapper
public abstract class PathMapper {

    public static final PathMapper INSTANCE = Mappers.getMapper(PathMapper.class);

    /**
     * Convert {@link org.openkilda.pce.Path} to {@link org.openkilda.messaging.info.network.Path}.
     */
    public org.openkilda.messaging.info.network.Path map(org.openkilda.pce.Path path) {
        if (path == null || path.getSegments().isEmpty()) {
            return new org.openkilda.messaging.info.network.Path(0L, 0L, new ArrayList<>());
        }

        List<String> edges = path.getSegments().stream()
                .map(segment -> String.format("%s_%d ===> %s_%d",
                        segment.getSrcSwitchId(), segment.getSrcPort(),
                        segment.getDestSwitchId(), segment.getDestPort()))
                .collect(Collectors.toList());

        return new org.openkilda.messaging.info.network.Path(path.getMinAvailableBandwidth(),
                path.getLatency(), edges);
    }
}
