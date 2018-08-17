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

package org.openkilda.messaging.ctrl;

import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload of a Message representing Dump data from discovery bolt.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DumpStateBySwitchRequestData extends RequestData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch Id.
     */
    @JsonProperty("switch_id")
    private final SwitchId switchId;


    /**
     * Instance constructor.
     *
     * @param switchId switch_id for data filtering
     * @throws IllegalArgumentException if any of mandatory parameters is null
     */
    @JsonCreator
    public DumpStateBySwitchRequestData(
            @JsonProperty("action") final String action,
            @JsonProperty("switch_id") final SwitchId switchId) {
        super(action);
        this.switchId = switchId;
    }

    public SwitchId getSwitchId() {
        return switchId;
    }
}
