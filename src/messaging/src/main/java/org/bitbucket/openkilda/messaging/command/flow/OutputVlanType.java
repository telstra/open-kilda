package org.bitbucket.openkilda.messaging.command.flow;

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
     * Constructs entity.
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


