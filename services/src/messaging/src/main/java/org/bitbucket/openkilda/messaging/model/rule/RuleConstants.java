package org.bitbucket.openkilda.messaging.model.rule;

import org.bitbucket.openkilda.messaging.Utils;

public final class RuleConstants {
    /**
     * The command name property.
     */
    public static final String OF_COMMAND = "of_command";

    /** The payload property. */
    public static final String FLOW_ID = Utils.FLOW_ID;

    /** The payload property. */
    public static final String SWITCH_ID = "switch_id";

    /** The payload property. */
    public static final String COOKIE = "cookie";

    /** The payload property. */
    public static final String OUTPUT_PORT = "output_port";

    /** The payload property. */
    public static final String OUTPUT_VLAN = "output_vlan";

    /** The payload property. */
    public static final String OUTPUT_VLAN_TYPE = "output_vlan_type";

    /** The payload property. */
    public static final String MATCH_PORT = "match_port";

    /** The payload property. */
    public static final String MATCH_VLAN = "match_vlan";

    /** The payload property. */
    public static final String METER_ID = "meter_id";

    /** The payload property. */
    public static final String BANDWIDTH = "bandwidth";

    /** The payload property. */
    public static final String PRIORITY = "priority";
}
