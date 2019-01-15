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

package org.openkilda.messaging.nbtopology.request;

import org.openkilda.messaging.nbtopology.annotations.ReadRequest;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@SuppressWarnings("squid:MaximumInheritanceDepth")
@ReadRequest
@Getter
@ToString
public class UpdateSwitchUnderMaintenanceRequest extends SwitchesBaseRequest {

    @JsonProperty("switch_id")
    private SwitchId switchId;

    @JsonProperty("under_maintenance")
    private boolean underMaintenance;

    public UpdateSwitchUnderMaintenanceRequest(@JsonProperty("switch_id") SwitchId switchId,
                                               @JsonProperty("under_maintenance") boolean underMaintenance) {
        this.switchId = switchId;
        this.underMaintenance = underMaintenance;
    }
}
