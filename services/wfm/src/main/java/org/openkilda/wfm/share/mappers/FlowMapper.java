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

import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowState;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link org.openkilda.model.Flow} to {@link Flow} and back.
 */
@Mapper
public interface FlowMapper {

    FlowMapper INSTANCE = Mappers.getMapper(FlowMapper.class);

    @Mappings({
            @Mapping(source = "srcSwitchId", target = "sourceSwitch"),
            @Mapping(source = "destSwitchId", target = "destinationSwitch"),
            @Mapping(source = "status", target = "state")
    })
    Flow map(org.openkilda.model.Flow flow);

    @Mappings({
            @Mapping(source = "sourceSwitch", target = "srcSwitchId"),
            @Mapping(source = "destinationSwitch", target = "destSwitchId"),
            @Mapping(source = "state", target = "status")
    })
    org.openkilda.model.Flow map(Flow flow);

    /**
     * Convert {@link org.openkilda.model.SwitchId} to {@link org.openkilda.messaging.model.SwitchId}.
     */
    default org.openkilda.messaging.model.SwitchId map(org.openkilda.model.SwitchId value) {
        if (value == null) {
            return null;
        }

        return new org.openkilda.messaging.model.SwitchId(value.toString());
    }

    /**
     * Convert {@link org.openkilda.messaging.model.SwitchId} to {@link org.openkilda.model.SwitchId}.
     */
    default org.openkilda.model.SwitchId map(org.openkilda.messaging.model.SwitchId value) {
        if (value == null) {
            return null;
        }

        return new org.openkilda.model.SwitchId(value.toString());
    }

    /**
     * Convert {@link org.openkilda.model.FlowStatus} to {@link FlowState}.
     */
    default FlowState map(org.openkilda.model.FlowStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case IN_PROGRESS:
                return FlowState.IN_PROGRESS;
            case UP:
                return FlowState.UP;
            case DOWN:
                return FlowState.DOWN;
        }
    }

    /**
     * Convert {@link FlowState} to {@link org.openkilda.model.FlowStatus}.
     */
    default org.openkilda.model.FlowStatus map(FlowState status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case IN_PROGRESS:
                return org.openkilda.model.FlowStatus.IN_PROGRESS;
            case UP:
                return org.openkilda.model.FlowStatus.UP;
            case DOWN:
                return org.openkilda.model.FlowStatus.DOWN;
        }
    }
}
