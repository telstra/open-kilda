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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.FlowPath;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link FlowPath} to {@link PathInfoData} and back.
 */
@Mapper
public abstract class FlowPathMapper {

    public static final FlowPathMapper INSTANCE = Mappers.getMapper(FlowPathMapper.class);

    @Mapping(source = "nodes", target = "path")
    public abstract PathInfoData map(FlowPath path);

    @Mapping(source = "path", target = "nodes")
    public abstract FlowPath map(PathInfoData path);
}
