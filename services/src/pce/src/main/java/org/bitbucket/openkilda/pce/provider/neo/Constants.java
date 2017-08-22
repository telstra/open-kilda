package org.bitbucket.openkilda.pce.provider.neo;

public class Constants {
    static final String CLEAN_FORMATTER_PATTERN = "MATCH (n) DETACH DELETE n";

    /**
     * Path query formatter pattern.
     */
    static final String PATH_QUERY_FORMATTER_PATTERN =
            "MATCH (a:switch{{name:'%s'}}),(b:switch{{name:'%s'}}), " +
                    "p = shortestPath((a)-[r:isl*..100]->(b)) " +
                    "where ALL(x in nodes(p) WHERE x.state = 'active') " +
                    "AND ALL(y in r WHERE y.available_bandwidth >= %d) " +
                    "RETURN p";

    /**
     * Update Isl available bandwidth query formatter pattern.
     */
    static final String AVAILABLE_BANDWIDTH_UPDATE_FORMATTER_PATTERN =
            "MATCH (a:switch)-[r:isl {{src_switch: '%s', src_port: '%s'}}]->(b:switch) " +
                    "set r.available_bandwidth = r.available_bandwidth - %d return r";

    /**
     * Get switch query formatter pattern.
     */
    static final String GET_SWITCH_FORMATTER_PATTERN = "MATCH (n:switch { name: '%s' }) RETURN n";

    /**
     * Create switch query formatter pattern.
     */
    static final String CREATE_SWITCH_FORMATTER_PATTERN =
            "CREATE (n:switch { " +
                    "name: '%s', " +
                    "state: '%s', " +
                    "address: '%s', " +
                    "hostname: '%s', " +
                    "controller: '%s', " +
                    "description: '%s'" +
                    "}) RETURN n";

    /**
     * Delete switch query formatter pattern.
     */
    static final String DELETE_SWITCH_FORMATTER_PATTERN =
            "MATCH (n:switch { name: '%s'}) DETACH DELETE n RETURN n";

    /**
     * Update switch query formatter pattern.
     */
    static final String UPDATE_SWITCH_FORMATTER_PATTERN =
            "MATCH (n:switch { name: '%s' }) SET " +
                    "n.name = '%s', " +
                    "n.state = '%s', " +
                    "n.address = '%s', " +
                    "n.hostname = '%s', " +
                    "n.controller = '%s', " +
                    "n.description = '%s'";

    /**
     * Dump switches query formatter pattern.
     */
    static final String DUMP_SWITCH_FORMATTER_PATTERN =
            "MATCH (n:switch) RETURN n";
}
