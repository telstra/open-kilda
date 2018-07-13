package org.openkilda.constants;

/**
 * 
 * The entity OPENTSDB Interface.
 *
 * @author Gaurav Chugh
 *
 */
public abstract class OpenTsDB {

    public static final String GROUP_BY = "true";
    public static final String TYPE = "literal_or";
    public static final String TYPE_WILDCARD = "wildcard";
    public static final String RATE = "true";
    public static final String AGGREGATOR = "sum";

    public enum StatsType {
        SWITCH, PORT, FLOW, ISL, ISL_LOSS_PACKET, 
        FLOW_LOSS_PACKET, FLOW_RAW_PACKET, SWITCH_PORT
    }

}
