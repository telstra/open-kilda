package org.bitbucket.openkilda.wfm.topology;

import org.apache.storm.generated.StormTopology;

/**
 * Represents topology interface.
 */
public interface Topology {
    /**
     * Zookeeper hosts property name.
     */
    String PROPERTY_ZOOKEEPER = "zookeeper.hosts";

    /**
     * Kafka hosts property name.
     */
    String PROPERTY_KAFKA = "kafka.hosts";

    /**
     * Parallelism value property name.
     */
    String PROPERTY_PARALLELISM = "parallelism";

    /**
     * Workers value property name.
     */
    String PROPERTY_WORKERS = "workers";

    /**
     * Default topologies configuration file.
     */
    String TOPOLOGY_PROPERTIES = "topology.properties";

    /**
     * Topology creator.
     * Should be implemented.
     *
     * @return {@link StormTopology}
     */
    StormTopology createTopology();

    /**
     * Returns topology name.
     * Default implementation.
     *
     * @return topology name
     */
    default String getTopologyName() {
        return getClass().getSimpleName().toLowerCase();
    }

    /**
     * Returns topology specific property name.
     * Adds topology name to property name.
     * Default implementation.
     *
     * @param propertyName property name
     * @return topology specific property name
     */
    default String getTopologyPropertyName(final String propertyName) {
        return String.format("%s.%s", getTopologyName(), propertyName);
    }
}
