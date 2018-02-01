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
    public static final String RATE = "true";
    public static final String AGGREGATOR = "sum";

    public enum StatsType {
        SWITCH, PORT, FLOW, ISL
    }

}
