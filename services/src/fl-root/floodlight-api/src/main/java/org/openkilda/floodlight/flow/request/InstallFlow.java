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

package org.openkilda.floodlight.flow.request;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InstallFlow extends FlowRequest {

    /**
     * Cookie allocated for flow.
     */
    @JsonProperty("cookie")
    protected Long cookie;

    /**
     * Input port for flow action.
     */
    @JsonProperty("input_port")
    protected Integer inputPort;

    /**
     * Output port for flow action.
     */
    @JsonProperty("output_port")
    protected Integer outputPort;

    public InstallFlow(MessageContext context, String id, Long cookie, SwitchId switchId, Integer inputPort,
                       Integer outputPort) {
        super(context, id, switchId);

        this.cookie = cookie;
        this.inputPort = inputPort;
        this.outputPort = outputPort;
    }
}
