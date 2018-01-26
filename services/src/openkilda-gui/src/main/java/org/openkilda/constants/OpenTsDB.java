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
    public static final String SWITCH_STATS="switchStats";
    public static final String PORT_STATS="portStats";
    public static final String FLOW_STATS="flowStats";
    public static final String ISL_STATS="islStats";
}
