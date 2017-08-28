package org.bitbucket.openkilda.messaging;

import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utils for flow commands.
 */
public final class Utils {
    /**
     * Common object mapper.
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * The request timestamp attribute.
     */
    public static final String TIMESTAMP = "timestamp";
    /**
     * The transaction ID property name.
     */
    public static final String TRANSACTION_ID = "transaction_id";
    /**
     * The correlation ID header name.
     */
    public static final String CORRELATION_ID = "correlation_id";
    /**
     * The destination property.
     */
    public static final String DESTINATION = "destination";
    /**
     * The payload property.
     */
    public static final String PAYLOAD = "payload";
    /**
     * The payload property.
     */
    public static final String FLOW_ID = "flowid";
    /**
     * The payload property.
     */
    public static final String FLOW_PATH = "flowpath";
    /**
     * The default correlation ID value.
     */
    public static final String DEFAULT_CORRELATION_ID = "admin-request";
    /**
     * The default correlation ID value.
     */
    public static final String SYSTEM_CORRELATION_ID = "system-request";
    /**
     * VLAN TAG Ether type value.
     */
    public static final int ETH_TYPE = 0x8100;
    /**
     * Minimum allowable VLAN ID value.
     */
    private static final int MIN_VLAN_ID = 0;
    /**
     * Maximum allowable VLAN ID value.
     */
    private static final int MAX_VLAN_ID = 4095;

    /**
     * Checks if specified vlan id is in allowable range.
     *
     * @param vlanId vlan id
     * @return true if vlan id is valid
     */
    public static boolean validateVlanRange(final Integer vlanId) {
        return (vlanId >= MIN_VLAN_ID) && (vlanId <= MAX_VLAN_ID);
    }

    /**
     * Validates output vlan operation type value by output vlan tag.
     *
     * @param outputVlanId   output vlan id
     * @param outputVlanType output vlan operation type
     * @return true if output vlan operation type is valid
     */
    public static boolean validateOutputVlanType(final Integer outputVlanId, final OutputVlanType outputVlanType) {
        return (outputVlanId != null && outputVlanId != 0) ?
                (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.REPLACE.equals(outputVlanType)) :
                (OutputVlanType.POP.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType));
    }

    /**
     * Validates output vlan operation type value by input vlan tag.
     *
     * @param inputVlanId    input vlan id
     * @param outputVlanType output vlan operation type
     * @return true if output vlan operation type is valid
     */
    public static boolean validateInputVlanType(final Integer inputVlanId, final OutputVlanType outputVlanType) {
        return (inputVlanId != null && inputVlanId != 0) ?
                (OutputVlanType.POP.equals(outputVlanType) || OutputVlanType.REPLACE.equals(outputVlanType)) :
                (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType));
    }

    /**
     * Validates switch id value.
     *
     * @param switchId switch id
     * @return true if switch id is valid
     */
    public static boolean validateSwitchId(final String switchId) {
        // TODO: check valid switch id
        return switchId != null && !switchId.isEmpty();
    }
}

