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

package org.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents types of messages.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum MessageType {
    /**
     * Command message type.
     */
    @JsonProperty("COMMAND")
    COMMAND("COMMAND"),

    /**
     * Information message type.
     */
    @JsonProperty("INFO")
    INFO("INFO"),

    /**
     * Error message type.
     */
    @JsonProperty("ERROR")
    ERROR("ERROR"),

    /**
     * Degug message type.
     */
    @JsonProperty("CTRL_REQUEST")
    CTRL_REQUEST("CTRL_REQUEST"),

    @JsonProperty("CTRL_RESPONSE")
    CTRL_RESPONSE("CTRL_RESPONSE");

    /**
     * Message type.
     */
    @JsonProperty("type")
    private final String type;

    /**
     * Instance constructor.
     *
     * @param type message type
     */
    @JsonCreator
    MessageType(@JsonProperty("type") final String type) {
        this.type = type;
    }

    /**
     * Returns message type.
     *
     * @return message type
     */
    public String getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return type;
    }
}
