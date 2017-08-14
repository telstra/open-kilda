package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;

import com.google.common.graph.MutableNetwork;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import java.util.LinkedList;
import java.util.Set;

public class NeoDriver implements NetworkStorage, FlowStorage, PathComputer {
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
     * Singleton {@link NeoDriver} instance.
     */
    private static final NeoDriver pathComputer = new NeoDriver();

    /**
     * {@link Driver} instance.
     */
    private static Driver driver;

    /**
     * Singleton {@link NeoDriver} instance constructor.
     */
    private NeoDriver() {
    }

    /**
     * Gets instance.
     *
     * @return singleton {@link NeoDriver} instance
     */
    public static NeoDriver getNeoDriver() {
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
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        // TODO: dump is used to fill the cache
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createFlow(ImmutablePair<Flow, Flow> flow) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFlow(String flowId) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateFlow(String flowId, ImmutablePair<Flow, Flow> flow) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<ImmutablePair<Flow, Flow>> dumpFlows() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Switch getSwitch(String switchId) {
        // TODO: dump is used to fill the cache
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSwitch(Switch newSwitch) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSwitch(String switchId) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateSwitch(String switchId, Switch newSwitch) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Switch> dumpSwitches() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Isl getIsl(String islId) {
        // TODO: dump is used to fill the cache
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createIsl(Isl isl) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteIsl(String islId) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateIsl(String islId, Isl isl) {
        // TODO: operation via Kafka message from FloodlightModules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Isl> dumpIsls() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedList<Isl> getPath(Switch srcSwitch, Switch dstSwitch, int bandwidth) {
        LinkedList<Isl> path = null;
        String query = String.format(PATH_QUERY_FORMATTER_PATTERN,
                srcSwitch.getSwitchId(), dstSwitch.getSwitchId(), bandwidth);
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
    public void updatePathBandwidth(LinkedList<Isl> path, int bandwidth) {
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getWeight(Isl isl) {
        return null;
    }
}
