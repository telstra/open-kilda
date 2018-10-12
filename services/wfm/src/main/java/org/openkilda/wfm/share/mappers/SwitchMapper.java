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

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link org.openkilda.model.Switch} to {@link SwitchInfoData} and back.
 */
@Mapper
public interface SwitchMapper {

    SwitchMapper INSTANCE = Mappers.getMapper(SwitchMapper.class);

    @Mapping(source = "status", target = "state")
    SwitchInfoData map(org.openkilda.model.Switch sw);

    @Mapping(source = "state", target = "status")
    org.openkilda.model.Switch map(SwitchInfoData sw);

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
     * Convert {@link org.openkilda.model.SwitchStatus} to {@link SwitchState}.
     */
    default SwitchState map(org.openkilda.model.SwitchStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case ACTIVE:
                return SwitchState.ACTIVATED;
            case INACTIVE:
                return SwitchState.DEACTIVATED;
            case REMOVED:
                return SwitchState.REMOVED;
        }
    }

    /**
     * Convert {@link SwitchState} to {@link org.openkilda.model.SwitchStatus}.
     */
    default org.openkilda.model.SwitchStatus map(SwitchState status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            default:
            case ACTIVATED:
            case ADDED:
            case CHANGED:
            case VALIDATING:
            case CACHED:
                return org.openkilda.model.SwitchStatus.ACTIVE;
            case DEACTIVATED:
                return org.openkilda.model.SwitchStatus.INACTIVE;
            case REMOVED:
                return org.openkilda.model.SwitchStatus.REMOVED;
        }
    }
}
