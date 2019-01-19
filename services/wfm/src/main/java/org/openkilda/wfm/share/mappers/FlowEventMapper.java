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

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.history.FlowEventData;
import org.openkilda.model.FlowPath;
import org.openkilda.model.history.FlowEvent;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.io.IOException;

/**
 * Convert {@link FlowEvent} to {@link FlowEventData}.
 */
@Mapper
public abstract class FlowEventMapper {

    public static final FlowEventMapper INSTANCE = Mappers.getMapper(FlowEventMapper.class);

    /**
     * Convert {@link FlowEventData} to {@link FlowEvent}.
     *
     * @param flowEventData the object to convert.
     * @return the result.
     */
    @Mapping(target = "sourceSwitch",
            expression = "java(new org.openkilda.model.SwitchId(flowEventData.getSourceSwitch()))")
    @Mapping(target = "destinationSwitch",
            expression = "java(new org.openkilda.model.SwitchId(flowEventData.getDestinationSwitch()))")
    public abstract FlowEvent map(FlowEventData flowEventData);

    /**
     * Convert flow path json to {@link FlowPath}.
     *
     * @param flowPath json string.
     * @return the result.
     * @throws IOException in case of parsing errors.
     */
    public FlowPath map(String flowPath) throws IOException {
        if (flowPath == null) {
            return null;
        }
        return MAPPER.readValue(flowPath, FlowPath.class);
    }
}
