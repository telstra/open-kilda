package org.bitbucket.openkilda.messaging.command.flow;

/**
 * Utils for flow commands.
 */
public final class Utils {
    public static final int ETH_TYPE = 0x8100;
    private static final int MIN_VLAN_ID = 0;
    private static final int MAX_VLAN_ID = 4095;

    /**
     * Checks if specified vlan id is in allowable range.
     *
     * @param vlanId vlan id
     * @return true if vlan id is valid
     */
    public static boolean validateVlanRange(int vlanId) {
        return (vlanId >= MIN_VLAN_ID) && (vlanId <= MAX_VLAN_ID);
    }

    /**
     * Validates output vlan operation type value by output vlan tag.
     *
     * @param outputVlanId   output vlan id
     * @param outputVlanType output vlan operation type
     * @return true if output vlan operation type is valid
     */
    public static boolean validateOutputVlanType(final Number outputVlanId, final OutputVlanType outputVlanType) {
        return (outputVlanId != null && outputVlanId.intValue() != 0) ?
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
    public static boolean validateInputVlanType(final Number inputVlanId, final OutputVlanType outputVlanType) {
        return (inputVlanId != null && inputVlanId.intValue() != 0) ?
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
