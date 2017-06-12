package org.bitbucket.openkilda.wfm.topology.stats;

/**
 * Represents components used in {@link StatsTopology}.
 */
public enum StatsComponentType {
    STATS_OFS_KAFKA_SPOUT,
    STATS_OFS_BOLT,
    PORT_STATS_METRIC_GEN,
    METER_CFG_STATS_METRIC_GEN,
    FLOW_STATS_METRIC_GEN,
    ERROR_BOLT
}
