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

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchStatus;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Convert {@link Switch} to {@link SwitchInfoData} and back.
 */
@Mapper
public abstract class SwitchMapper {

    public static final SwitchMapper INSTANCE = Mappers.getMapper(SwitchMapper.class);

    @Mapping(source = "status", target = "state")
    public abstract SwitchInfoData map(Switch sw);

    @Mapping(source = "state", target = "status")
    public abstract Switch map(SwitchInfoData sw);

    /**
     * Convert {@link SwitchStatus} to {@link SwitchChangeType}.
     */
    public SwitchChangeType map(SwitchStatus status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case ACTIVE:
                return SwitchChangeType.ACTIVATED;
            case INACTIVE:
                return SwitchChangeType.DEACTIVATED;
            case REMOVED:
                return SwitchChangeType.REMOVED;
            default:
                throw new IllegalArgumentException("Unsupported Switch status: " + status);
        }
    }

    /**
     * Convert {@link SwitchChangeType} to {@link SwitchStatus}.
     */
    public SwitchStatus map(SwitchChangeType status) {
        if (status == null) {
            return null;
        }

        switch (status) {
            case ACTIVATED:
            case ADDED:
            case CHANGED:
            case VALIDATING:
            case CACHED:
                return SwitchStatus.ACTIVE;
            case DEACTIVATED:
                return SwitchStatus.INACTIVE;
            case REMOVED:
                return SwitchStatus.REMOVED;
            default:
                throw new IllegalArgumentException("Unsupported Switch status: " + status);
        }
    }
}
