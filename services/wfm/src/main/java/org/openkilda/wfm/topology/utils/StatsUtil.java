package org.openkilda.wfm.topology.utils;

public class StatsUtil {

    public static String formatSwitchId(String switchId)
    {
        return "SW" + switchId.replaceAll(":", "").toUpperCase();
    }
}
