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

package org.bitbucket.openkilda.messaging.payload.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Enum represents output vlan operation types.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public enum OutputVlanType {
    /**
     * No operations with vlan tags.
     */
    NONE("NONE"),

    /**
     * Push vlan tag.
     */
    PUSH("PUSH"),

    /**
     * Pop vlan tag.
     */
    POP("POP"),

    /**
     * Replace vlan id.
     */
    REPLACE("REPLACE");

    /**
     * Output vlan type.
     */
    @JsonProperty("output_vlan_type")
    private final String outputVlanType;

    /**
     * Instance constructor.
     *
     * @param outputVlanType output vlan type
     */
    @JsonCreator
    OutputVlanType(@JsonProperty("output_vlan_type") final String outputVlanType) {
        this.outputVlanType = outputVlanType;
    }

    /**
     * Returns output vlan type.
     *
     * @return output vlan type
     */
    public String getDestination() {
        return this.outputVlanType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return outputVlanType;
    }
}


