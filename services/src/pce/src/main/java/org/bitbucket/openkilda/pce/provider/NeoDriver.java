/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;

import com.google.common.graph.MutableNetwork;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Statement;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NeoDriver implements PathComputer {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(NeoDriver.class);

    /**
     * Clean database query.
     */
    private static final String CLEAN_FORMATTER_PATTERN = "MATCH (n) DETACH DELETE n";

    /**
     * Path query formatter pattern.
     */
    private static final String PATH_QUERY_FORMATTER_PATTERN =
            "MATCH (a:switch{name:{src_switch}}),(b:switch{name:{dst_switch}}), " +
                    "p = shortestPath((a)-[r:isl*..100]->(b)) " +
                    "where ALL(x in nodes(p) WHERE x.state = 'active') " +
                    "AND ALL(y in r WHERE y.available_bandwidth >= {bandwidth}) " +
                    "RETURN p";

    /**
     * {@link Driver} instance.
     */
    private static Driver driver;

    private final String hostname;

    private final String username;

    private final String password;

    /**
     * Gets instance.
     */
    public NeoDriver() {
        this("neo4j", "neo4j", "temppass");
    }

    /**
     * Gets Driver instance.
     *
     * @param hostname database hostname
     * @param username database username
     * @param password database password
     */
    public NeoDriver(String hostname, String username, String password) {
        this.hostname = hostname;
        this.username = username;
        this.password = password;
    }

    /**
     * {@inheritDoc}
     */
    public void init() {
        if (driver == null) {
            String address = String.format("bolt://%s:7687", hostname);
            driver = GraphDatabase.driver(address, AuthTokens.basic(password, username));
            logger.info("NeoDriver created: {}", driver.toString());
        }
    }

    /**
     * Cleans database.
     */
    public void clean() {
        String query = CLEAN_FORMATTER_PATTERN;

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
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow) {
        return getPath(flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination,
                                                             int bandwidth) {
        return getPath(source.getSwitchId(), destination.getSwitchId(), bandwidth);
    }

    private ImmutablePair<PathInfoData, PathInfoData> getPath(String srcSwitch, String dstSwitch, int bandwidth) {
        long latency = 0L;
        List<PathNode> forwardNodes = new LinkedList<>();
        List<PathNode> reverseNodes = new LinkedList<>();

        if (!srcSwitch.equals(dstSwitch)) {
            Statement pathStatement = new Statement(PATH_QUERY_FORMATTER_PATTERN);
            Value value = Values.parameters("src_switch", srcSwitch, "dst_switch", dstSwitch, "bandwidth", bandwidth);

            if (driver != null) {
                Session session = driver.session();
                StatementResult result = session.run(pathStatement.withParameters(value));

                if (result.hasNext()) {

                    Record record = result.next();

                    if (record != null) {
                        LinkedList<Relationship> isls = new LinkedList<>();
                        record.fields().get(0).value().asPath().relationships().forEach(isls::add);

                        int seqId = 0;
                        for (Relationship isl : isls) {
                            latency += isl.get("latency").asLong();

                            forwardNodes.add(new PathNode(isl.get("src_switch").asString(),
                                    isl.get("src_port").asInt(), seqId, isl.get("latency").asLong()));
                            seqId++;

                            forwardNodes.add(new PathNode(isl.get("dst_switch").asString(),
                                    isl.get("dst_port").asInt(), seqId, 0L));
                            seqId++;
                        }

                        seqId = 0;
                        Collections.reverse(isls);

                        for (Relationship isl : isls) {
                            reverseNodes.add(new PathNode(isl.get("dst_switch").asString(),
                                    isl.get("dst_port").asInt(), seqId, isl.get("latency").asLong()));
                            seqId++;

                            reverseNodes.add(new PathNode(isl.get("src_switch").asString(),
                                    isl.get("src_port").asInt(), seqId, 0L));
                            seqId++;
                        }
                    }
                }

                session.close();

            } else {
                logger.error("NeoDriver was not created");
            }
        } else {
            logger.info("No path computation for one-switch flow");
        }

        return new ImmutablePair<>(new PathInfoData(latency, forwardNodes), new PathInfoData(latency, reverseNodes));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network) {
        return this;
    }
}
