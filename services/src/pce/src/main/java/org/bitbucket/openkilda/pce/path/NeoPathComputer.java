package org.bitbucket.openkilda.pce.path;

import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;

import com.google.common.graph.MutableNetwork;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import java.util.HashSet;
import java.util.Set;

public class NeoPathComputer implements PathComputer {
    /**
     * Path query formatter pattern.
     */
    private static final String PATH_QUERY_FORMATTER_PATTERN =
            "MATCH (a:switch{{name:'%s'}}),(b:switch{{name:'%s'}}), " +
            "p = shortestPath((a)-[r:isl*..100]->(b)) " +
            "where ALL(x in nodes(p) WHERE x.state = 'active') " +
            "AND ALL(y in r WHERE y.available_bandwidth >= %d) " +
            "RETURN p";

    /**
     * Update Isl available bandwidth query formatter pattern.
     */
    private static final String AVAILABLE_BANDWIDTH_UPDATE_FORMATTER_PATTERN =
            "MATCH (a:switch)-[r:isl {{src_switch: '%s', src_port: '%s'}}]->(b:switch) " +
            "set r.available_bandwidth = r.available_bandwidth - %d return r";
    /**
     * Singleton {@link NeoPathComputer} instance.
     */
    private static final NeoPathComputer pathComputer = new NeoPathComputer();

    /**
     * Network cache.
     */
    private static MutableNetwork<Switch, Isl> network;

    /**
     * {@link Driver} instance.
     */
    private static Driver driver;

    /**
     * Gets instance.
     *
     * @return singleton {@link NeoPathComputer} instance
     */
    public static NeoPathComputer getPathComputer() {
        if (driver == null) {
            // TODO: read from properties
            String username = "neo4j";
            String password = "temppass";
            String host = "neo4j";
            String address = String.format("bolt://%s:7687", host);
            driver = GraphDatabase.driver(address, AuthTokens.basic(username, password));
        }

        return pathComputer;
    }

    /**
     * Singleton {@link NeoPathComputer} instance constructor.
     */
    private NeoPathComputer() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        return getPath(srcSwitch.getSwitchId(), dstSwitch.getSwitchId(), bandwidth);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Isl> getPath(String srcSwitchId, String dstSwitchId, int bandwidth) {
        Set<Isl> path = null;
        String query = String.format(PATH_QUERY_FORMATTER_PATTERN, srcSwitchId, dstSwitchId, bandwidth);
        try (Session session = driver.session()) {
            StatementResult result = session.run(query);
            // result.forEachRemaining();
        } finally {
            driver.close();
        }
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Isl> getPathIntersection(Set<Isl> firstPath, Set<Isl> secondPath) {
        Set<Isl> intersection = new HashSet<>(firstPath);
        intersection.retainAll(secondPath);
        return intersection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePathBandwidth(Set<Isl> path, int bandwidth) {
        try (Session session = driver.session()) {
            for (Isl isl : path) {
                isl.setAvailableBandwidth(isl.getAvailableBandwidth() - bandwidth);
                String query = String.format(AVAILABLE_BANDWIDTH_UPDATE_FORMATTER_PATTERN,
                        isl.getSourceSwitch(), isl.getSourcePort(), bandwidth);
                StatementResult result = session.run(query);
            }
        } finally {
            driver.close();
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNetwork(MutableNetwork<Switch, Isl> network) {
        NeoPathComputer.network = network;
    }
}
