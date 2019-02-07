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

package org.openkilda.messaging.floodlight.request;

import org.openkilda.messaging.CommandContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;

class InstallMeteredRule extends InstallFlow {

    /**
     * Allocated meter id.
     */
    @JsonProperty("meter_id")
    protected Long meterId;

    /**
     * Flow bandwidth value.
     */
    @JsonProperty("bandwidth")
    protected Long bandwidth;

    public InstallMeteredRule(CommandContext context, String id, Long cookie, SwitchId switchId, Integer inputPort,
                              Integer outputPort, Long meterId, Long bandwidth) {
        super(context, id, cookie, switchId, inputPort, outputPort);
        this.meterId = meterId;
        this.bandwidth = bandwidth;
    }
}
