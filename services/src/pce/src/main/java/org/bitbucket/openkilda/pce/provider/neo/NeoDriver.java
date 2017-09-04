package org.bitbucket.openkilda.pce.provider.neo;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.pce.provider.FlowStorage;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import com.google.common.graph.MutableNetwork;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class NeoDriver implements NetworkStorage, FlowStorage, PathComputer {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

    /**
     * {@link Driver} instance.
     */
    private static Driver driver;

    /**
     * Gets instance.
     */
    public NeoDriver() {
        if (driver == null) {
            // TODO: read from properties
            String username = "neo4j";
            String password = "temppass";
            String host = "neo4j";
            String address = String.format("bolt://%s:7687", host);
            driver = GraphDatabase.driver(address, AuthTokens.basic(username, password));
        }
    }

    /**
     * Cleans database.
     */
    public void clean() {
        String query = Constants.CLEAN_FORMATTER_PATTERN;

        logger.info("Clean query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        logger.info("Switches deleted: {}", String.valueOf(result.summary().counters().nodesDeleted()));
        logger.info("Isl deleted: {}", String.valueOf(result.summary().counters().relationshipsDeleted()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createFlow(ImmutablePair<Flow, Flow> flow) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteFlow(String flowId) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateFlow(String flowId, ImmutablePair<Flow, Flow> flow) {
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
    public SwitchInfoData getSwitch(String switchId) {
        SwitchInfoData sw = null;
        String query = String.format(Constants.GET_SWITCH_FORMATTER_PATTERN, switchId);

        logger.info("Get switch query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        if (result.hasNext()) {
            Value value = result.next().get("n");
            sw = new SwitchInfoData(value.asMap());
            logger.info("Switch found: {}", sw.toString());
        } else {
            logger.info("Switch {} was not found", switchId);
        }

        return sw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSwitch(SwitchInfoData sw) {
        String query = String.format(Constants.CREATE_SWITCH_FORMATTER_PATTERN,
                sw.getSwitchId(), sw.getState(), sw.getAddress(),
                sw.getHostname(), sw.getController(), sw.getDescription());

        logger.info("Create switch query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        logger.info("Switches created: {}", String.valueOf(result.summary().counters().nodesCreated()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSwitch(String switchId) {
        String query = String.format(Constants.DELETE_SWITCH_FORMATTER_PATTERN, switchId);

        logger.info("Delete switch query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        logger.info("Switches deleted: {}", String.valueOf(result.summary().counters().nodesDeleted()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateSwitch(String switchId, SwitchInfoData newSwitch) {
        String query = String.format(Constants.UPDATE_SWITCH_FORMATTER_PATTERN, switchId,
                newSwitch.getSwitchId(), newSwitch.getState(), newSwitch.getAddress(),
                newSwitch.getHostname(), newSwitch.getController(), newSwitch.getDescription());

        logger.info("Update switch query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        logger.info("Switch properties updated: {}", String.valueOf(result.summary().counters().propertiesSet()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<SwitchInfoData> dumpSwitches() {
        Set<SwitchInfoData> switches = new HashSet<>();
        String query = Constants.DUMP_SWITCH_FORMATTER_PATTERN;

        logger.info("Dump switch query: {}", query);

        Session session = driver.session();
        StatementResult result = session.run(query);
        session.close();

        for (Record record : result.list()) {
            Value value = record.get("n");
            switches.add(new SwitchInfoData(value.asMap()));
        }

        logger.info("Switches: {}", switches.toString());

        return switches;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IslInfoData getIsl(String islId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createIsl(IslInfoData isl) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteIsl(String islId) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateIsl(String islId, IslInfoData isl) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<IslInfoData> dumpIsls() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PathInfoData getPath(SwitchInfoData srcSwitch, SwitchInfoData dstSwitch, int bandwidth) {
        PathInfoData path = null;

        /*String query = String.format(Constants.PATH_QUERY_FORMATTER_PATTERN,
                srcSwitch.getSwitchId(), dstSwitch.getSwitchId(), bandwidth);
        Session session = driver.session();
        StatementResult result = session.run(query);
        result.forEachRemaining();

        session.close();*/

        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePathBandwidth(PathInfoData path, int bandwidth) {
        /*Session session = driver.session();
        for (Isl isl : path) {
            isl.setAvailableBandwidth(isl.getAvailableBandwidth() - bandwidth);
            String query = String.format(Constants.AVAILABLE_BANDWIDTH_UPDATE_FORMATTER_PATTERN,
                    isl.getSourceSwitch(), isl.getSourcePort(), bandwidth);
            StatementResult result = session.run(query);
        }
        session.close();*/
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getWeight(IslInfoData isl) {
        return 1L;
    }
}
