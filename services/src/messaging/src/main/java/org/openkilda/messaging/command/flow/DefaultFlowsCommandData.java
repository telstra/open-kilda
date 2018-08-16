/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.command.flow;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Defines the payload payload of a Message representing an command for default flows installation.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "command",
        "switch_id"})
public class DefaultFlowsCommandData extends CommandData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Switch id for default flows installation.
     */
    @JsonProperty("switch_id")
    protected SwitchId switchId;

    /**
     * Default constructor.
     */
    public DefaultFlowsCommandData() {
    }

    /**
     * Instance constructor.
     *
     * @param switchId switch id to install default flows on
     */
    @JsonCreator
    public DefaultFlowsCommandData(@JsonProperty("switch_id") final SwitchId switchId) {
        this.switchId = switchId;
    }

    /**
     * Returns switch id.
     *
     * @return switch id
     */
    public SwitchId getSwitchId() {
        return switchId;
    }

    /**
     * Sets switch id.
     *
     * @param switchId switch id to set
     */
    public void setSwitchId(final SwitchId switchId) {
        this.switchId = switchId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return switchId.toString();
    }
}
