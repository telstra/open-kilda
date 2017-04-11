package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.floodlight.switchmanager.OutputVlanType;

/**
 * Created by atopilin on 11/04/2017.
 */
public final class Utils {
    public static final int ETH_TYPE = 0x8100;
    private static final int MIN_VLAN_ID = 0;
    private static final int MAX_VLAN_ID = 4095;

    public static boolean checkVlanRange(int vlanId) {
        return (vlanId >= MIN_VLAN_ID) && (vlanId <= MAX_VLAN_ID);
    }

    public static boolean checkOutputVlanType(Number outputVlanId, String outputVlanType) {
       return (outputVlanId != null && outputVlanId.intValue() != 0) ?
               (OutputVlanType.PUSH.toString().equals(outputVlanType)
                       || OutputVlanType.REPLACE.toString().equals(outputVlanType)) :
               (OutputVlanType.POP.toString().equals(outputVlanType)
                       || OutputVlanType.NONE.toString().equals(outputVlanType));
    }

    public static boolean checkSwitchId(String switchId) {
        // TODO: check valid switch id
        return !switchId.isEmpty();
    }
}
