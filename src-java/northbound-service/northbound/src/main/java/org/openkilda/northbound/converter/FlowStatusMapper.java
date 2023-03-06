/* Copyright 2021 Telstra Open Source
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

import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class FlowStatusMapper {
    /**
     * Convert {@link FlowPathStatus} to {@link String}.
     */
    public String map(FlowPathStatus flowPathStatus) {
        if (flowPathStatus == null) {
            return null;
        }

        switch (flowPathStatus) {
            case ACTIVE:
                return "Up";
            case INACTIVE:
                return "Down";
            case IN_PROGRESS:
                return "In progress";
            default:
                return flowPathStatus.toString().toLowerCase();
        }
    }

    /**
     * Convert {@link FlowState} to {@link String}.
     */
    public String map(FlowState state) {
        if (state == null) {
            return null;
        }

        return state.getState();
    }

    /**
     * Convert {@link FlowStatus} to {@link String}.
     */
    public String map(FlowStatus flowStatus) {
        if (flowStatus == null) {
            return null;
        }

        switch (flowStatus) {
            case UP:
                return "Up";
            case DOWN:
                return "Down";
            case IN_PROGRESS:
                return "In progress";
            case DEGRADED:
                return "Degraded";
            default:
                return flowStatus.toString().toLowerCase();
        }
    }
}
