/* Copyright 2018 Telstra Open Source
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

package org.openkilda.pce.impl;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathComputerFactory.WeightStrategy;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.BestCostAndShortestPathFinder;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;

import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.neo4j.ogm.testutil.TestServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryPathComputerTest {

    static TestServer testServer;
    static TransactionManager txManager;
    static SwitchRepository switchRepository;
    static IslRepository islRepository;
    static FlowPathRepository flowPathRepository;

    private static JdkProxyStaticConfigurationFactory configFactory = new JdkProxyStaticConfigurationFactory();
    static PathComputerConfig config;

    static AvailableNetworkFactory availableNetworkFactory;
    static PathComputerFactory pathComputerFactory;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() {
        testServer = new TestServer(true, true, 5);

        PersistenceManager persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() { //NOSONAR
                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> T getConfiguration(Class<T> configurationType) {
                        if (configurationType.equals(Neo4jConfig.class)) {
                            return (T) new Neo4jConfig() {
                                @Override
                                public String getUri() {
                                    return testServer.getUri();
                                }

                                @Override
                                public String getLogin() {
                                    return testServer.getUsername();
                                }

                                @Override
                                public String getPassword() {
                                    return testServer.getPassword();
                                }

                                @Override
                                public int getConnectionPoolSize() {
                                    return 50;
                                }

                                @Override
                                public String getIndexesAuto() {
                                    return null;
                                }
                            };
                        } else {
                            throw new UnsupportedOperationException("Unsupported configurationType "
                                    + configurationType);
                        }
                    }
                });

        txManager = persistenceManager.getTransactionManager();

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        islRepository = repositoryFactory.createIslRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();

        config = configFactory.createConfiguration(PathComputerConfig.class, new MapConfigurationSource(emptyMap()));

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
        pathComputerFactory = new PathComputerFactory(config, availableNetworkFactory);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @After
    public void cleanUp() {
        ((Neo4jSessionFactory) txManager).getSession().purgeDatabase();
    }

    @Test
    public void shouldFindPathOverDiamondWithAllActiveLinksByCost()
            throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20);

        Switch srcSwitch = switchRepository.findById(new SwitchId("00:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("00:04")).get();

        UnidirectionalFlow f = new FlowPair(srcSwitch, destSwitch).getForward();
        f.setBandwidth(100);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        assertEquals(new SwitchId("00:02"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path B
    }

    @Test
    public void shouldFindPathOverDiamondWithOneActiveRouteByCost()
            throws UnroutableFlowException, RecoverableException {
        /*
         * verifies that iSL in both directions needs to be active
         */
        createDiamond(IslStatus.INACTIVE, IslStatus.ACTIVE, 10, 20, "01:", 1);

        Switch srcSwitch = switchRepository.findById(new SwitchId("01:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("01:04")).get();

        UnidirectionalFlow f = new FlowPair(srcSwitch, destSwitch).getForward();
        f.setBandwidth(100);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair path = pathComputer.getPath(f);

        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // ====> only difference is it should now have C as first hop .. since B is inactive
        assertEquals(new SwitchId("01:03"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path B
    }

    @Test
    public void shouldFindPathOverTriangleWithOneActiveRouteByCost()
            throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but lowest path is inactive
         */
        createTriangleTopo(IslStatus.INACTIVE, 5, 20, "02:", 1);

        Switch srcSwitch = switchRepository.findById(new SwitchId("02:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("02:02")).get();

        UnidirectionalFlow f = new FlowPair(srcSwitch, destSwitch).getForward();
        f.setBandwidth(100);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // ====> only difference is it should now have C as first hop .. since B is inactive
        assertEquals(new SwitchId("02:03"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path B
    }

    @Test
    public void shouldFindPathOverDiamondWithNoCostOnOneRoute() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, -1, 2000, "03:", 1);

        Switch srcSwitch = switchRepository.findById(new SwitchId("03:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("03:04")).get();

        UnidirectionalFlow f = new FlowPair(srcSwitch, destSwitch).getForward();
        f.setBandwidth(100);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // ====> Should choose B .. because default cost (700) cheaper than 2000
        assertEquals(new SwitchId("03:02"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path B
    }


    @Test
    public void shouldFailToFindOverDiamondWithNoActiveRoutes() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond(IslStatus.INACTIVE, IslStatus.INACTIVE, 10, 30, "04:", 1);

        Switch srcSwitch = switchRepository.findById(new SwitchId("04:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("04:04")).get();

        UnidirectionalFlow f = new FlowPair(srcSwitch, destSwitch).getForward();
        f.setBandwidth(100);

        thrown.expect(UnroutableFlowException.class);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        pathComputer.getPath(f);
    }


    @Test
    public void shouldFindPathOverDiamondWithAllActiveLinksAndIgnoreBandwidth()
            throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "05:", 1);

        Switch srcSwitch1 = switchRepository.findById(new SwitchId("05:01")).get();
        Switch destSwitch1 = switchRepository.findById(new SwitchId("05:03")).get();

        UnidirectionalFlow f1 = new FlowPair(srcSwitch1, destSwitch1).getForward();
        f1.setBandwidth(0);
        f1.setIgnoreBandwidth(false);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair path = pathComputer.getPath(f1);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(1));

        Switch srcSwitch2 = switchRepository.findById(new SwitchId("05:01")).get();
        Switch destSwitch2 = switchRepository.findById(new SwitchId("05:04")).get();

        UnidirectionalFlow f2 = new FlowPair(srcSwitch2, destSwitch2).getForward();
        f2.setBandwidth(0);
        f2.setIgnoreBandwidth(false);

        path = pathComputer.getPath(f2);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        assertEquals(new SwitchId("05:02"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    /**
     * Create a couple of islands .. try to find a path between them .. validate no path is returned, and that the
     * function completes in reasonable time ( < 10ms);
     */
    @Test
    public void shouldFailToFindOverIslandsWithAllActiveLinksAndIgnoreBandwidth()
            throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "06:", 1);
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "07:", 1);

        Switch srcSwitch1 = switchRepository.findById(new SwitchId("06:01")).get();
        Switch destSwitch1 = switchRepository.findById(new SwitchId("06:03")).get();

        // THIS ONE SHOULD WORK
        UnidirectionalFlow f1 = new FlowPair(srcSwitch1, destSwitch1).getForward();
        f1.setBandwidth(0);
        f1.setIgnoreBandwidth(false);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair path = pathComputer.getPath(f1);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(1));

        Switch srcSwitch2 = switchRepository.findById(new SwitchId("06:01")).get();
        Switch destSwitch2 = switchRepository.findById(new SwitchId("07:04")).get();

        // THIS ONE SHOULD FAIL
        UnidirectionalFlow f2 = new FlowPair(srcSwitch2, destSwitch2).getForward();
        f2.setBandwidth(0);
        f2.setIgnoreBandwidth(false);

        thrown.expect(UnroutableFlowException.class);

        pathComputer.getPath(f2);
    }

    /**
     * See how it works with a large network. It takes a while to create the network .. therefore @Ignore so that it
     * doesn't slow down unit tests.
     */
    @Test
    @Ignore
    public void shouldFindOverLargeIslands() throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "08:", 1);

        for (int i = 0; i < 50; i++) {
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "10:", 4 * i + 1);
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "11:", 4 * i + 1);
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "12:", 4 * i + 1);
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "13:", 4 * i + 1);
        }
        for (int i = 0; i < 49; i++) {
            String prev = String.format("%02X", 4 * i + 4);
            String next = String.format("%02X", 4 * i + 5);
            connectDiamonds(new SwitchId("10:" + prev), new SwitchId("10:" + next), IslStatus.ACTIVE, 20, 50);
            connectDiamonds(new SwitchId("11:" + prev), new SwitchId("11:" + next), IslStatus.ACTIVE, 20, 50);
            connectDiamonds(new SwitchId("12:" + prev), new SwitchId("12:" + next), IslStatus.ACTIVE, 20, 50);
            connectDiamonds(new SwitchId("13:" + prev), new SwitchId("13:" + next), IslStatus.ACTIVE, 20, 50);
        }
        connectDiamonds(new SwitchId("10:99"), new SwitchId("11:22"), IslStatus.ACTIVE, 20, 50);
        connectDiamonds(new SwitchId("11:99"), new SwitchId("12:22"), IslStatus.ACTIVE, 20, 50);
        connectDiamonds(new SwitchId("12:99"), new SwitchId("13:22"), IslStatus.ACTIVE, 20, 50);
        connectDiamonds(new SwitchId("13:99"), new SwitchId("10:22"), IslStatus.ACTIVE, 20, 50);

        Switch srcSwitch1 = switchRepository.findById(new SwitchId("10:01")).get();
        Switch destSwitch1 = switchRepository.findById(new SwitchId("11:03")).get();

        // THIS ONE SHOULD WORK
        UnidirectionalFlow f1 = new FlowPair(srcSwitch1, destSwitch1).getForward();
        f1.setBandwidth(0);
        f1.setIgnoreBandwidth(false);

        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestCostAndShortestPathFinder(200,
                        pathComputerFactory.getWeightFunctionByStrategy(WeightStrategy.COST)));
        PathPair path = pathComputer.getPath(f1, false);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(278));

        Switch srcSwitch2 = switchRepository.findById(new SwitchId("08:01")).get();
        Switch destSwitch2 = switchRepository.findById(new SwitchId("11:04")).get();

        // THIS ONE SHOULD FAIL
        UnidirectionalFlow f2 = new FlowPair(srcSwitch2, destSwitch2).getForward();
        f2.setBandwidth(0);
        f2.setIgnoreBandwidth(false);

        thrown.expect(UnroutableFlowException.class);

        pathComputer.getPath(f2);
    }

    /**
     * This verifies that the getPath in PathComputer returns what we expect. Essentially, this tests the additional
     * logic wrt taking the results of the algo and convert to something installable.
     */
    @Test
    public void verifyConversionToPair() throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "09:", 1);

        Switch srcSwitch = switchRepository.findById(new SwitchId("09:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("09:04")).get();

        UnidirectionalFlow flow = new FlowPair(srcSwitch, destSwitch).getForward();
        flow.setIgnoreBandwidth(false);
        flow.setBandwidth(10);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair result = pathComputer.getPath(flow);
        assertNotNull(result);
        // ensure start/end switches match
        List<Path.Segment> left = result.getForward().getSegments();
        assertEquals(srcSwitch.getSwitchId(), left.get(0).getSrcSwitchId());
        assertEquals(destSwitch.getSwitchId(), left.get(left.size() - 1).getDestSwitchId());

        List<Path.Segment> right = result.getReverse().getSegments();
        assertEquals(destSwitch.getSwitchId(), right.get(0).getSrcSwitchId());
        assertEquals(srcSwitch.getSwitchId(), right.get(right.size() - 1).getDestSwitchId());
    }

    /**
     * Checks that existed flow should always have available path even there is only links with 0 available bandwidth.
     */
    @Test
    public void shouldAlwaysFindPathForExistedFlow() throws RecoverableException, UnroutableFlowException {
        String flowId = "flow-A1:01-A1:03";
        long bandwidth = 1000;

        createLinearTopoWithFlowSegments(10, "A1:", 1, 0L,
                flowId, bandwidth);

        Switch srcSwitch = switchRepository.findById(new SwitchId("A1:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("A1:03")).get();

        UnidirectionalFlow flow = new FlowPair(srcSwitch, destSwitch).getForward();
        flow.setFlowId(flowId);
        flow.setBandwidth(bandwidth);
        flow.setIgnoreBandwidth(false);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair result = pathComputer.getPath(flow, true);

        assertThat(result.getForward().getSegments(), Matchers.hasSize(2));
        assertThat(result.getReverse().getSegments(), Matchers.hasSize(2));
    }

    /**
     * Tests the case when we try to increase bandwidth of the flow and there is no available bandwidth left.
     */
    @Test
    public void shouldNotFindPathForExistedFlowAndIncreasedBandwidth()
            throws RecoverableException, UnroutableFlowException {
        String flowId = "flow-A1:01-A1:03";
        long originFlowBandwidth = 1000L;

        // create network, all links have available bandwidth 0
        createLinearTopoWithFlowSegments(10, "A1:", 1, 0,
                flowId, originFlowBandwidth);

        Switch srcSwitch = switchRepository.findById(new SwitchId("A1:01")).get();
        Switch destSwitch = switchRepository.findById(new SwitchId("A1:03")).get();

        UnidirectionalFlow flow = new FlowPair(srcSwitch, destSwitch).getForward();
        flow.setBandwidth(originFlowBandwidth);
        flow.setIgnoreBandwidth(false);
        flow.setFlowId(flowId);

        long updatedFlowBandwidth = originFlowBandwidth + 1;
        flow.setBandwidth(updatedFlowBandwidth);

        thrown.expect(UnroutableFlowException.class);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        pathComputer.getPath(flow, true);
    }

    private void createLinearTopoWithFlowSegments(int cost, String switchStart, int startIndex, long linkBw,
                                                  String flowId, long flowBandwidth) {
        // A - B - C
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index));

        createIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 5);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 6);
        createIsl(nodeC, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 6);
        createIsl(nodeB, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 5);

        FlowPath forwardPath = new FlowPath();
        forwardPath.setSrcSwitch(nodeA);
        forwardPath.setDestSwitch(nodeC);
        forwardPath.setPathId(new PathId(UUID.randomUUID().toString()));
        forwardPath.setFlowId(flowId);
        forwardPath.setBandwidth(flowBandwidth);
        forwardPath.setSegments(new ArrayList<>());
        addPathSegment(forwardPath, nodeA, nodeB, 5, 5);
        addPathSegment(forwardPath, nodeB, nodeC, 6, 6);
        flowPathRepository.createOrUpdate(forwardPath);

        FlowPath reversePath = new FlowPath();
        reversePath.setSrcSwitch(nodeC);
        reversePath.setDestSwitch(nodeA);
        reversePath.setPathId(new PathId(UUID.randomUUID().toString()));
        reversePath.setFlowId(flowId);
        reversePath.setBandwidth(flowBandwidth);
        reversePath.setSegments(new ArrayList<>());
        addPathSegment(reversePath, nodeC, nodeB, 6, 6);
        addPathSegment(reversePath, nodeB, nodeA, 5, 5);
        flowPathRepository.createOrUpdate(reversePath);
    }

    private void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost) {
        createDiamond(pathBstatus, pathCstatus, pathBcost, pathCcost, "00:", 1);
    }

    private void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost,
                               String switchStart, int startIndex) {
        // A - B - D
        //   + C +
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + String.format("%02X", index));

        IslStatus actual = (pathBstatus == IslStatus.ACTIVE) && (pathCstatus == IslStatus.ACTIVE)
                ? IslStatus.ACTIVE : IslStatus.INACTIVE;
        createIsl(nodeA, nodeB, pathBstatus, actual, pathBcost, 1000, 5);
        createIsl(nodeA, nodeC, pathCstatus, actual, pathCcost, 1000, 6);
        createIsl(nodeB, nodeD, pathBstatus, actual, pathBcost, 1000, 6);
        createIsl(nodeC, nodeD, pathCstatus, actual, pathCcost, 1000, 5);
        createIsl(nodeB, nodeA, pathBstatus, actual, pathBcost, 1000, 5);
        createIsl(nodeC, nodeA, pathCstatus, actual, pathCcost, 1000, 6);
        createIsl(nodeD, nodeB, pathBstatus, actual, pathBcost, 1000, 6);
        createIsl(nodeD, nodeC, pathCstatus, actual, pathCcost, 1000, 5);
    }

    private void connectDiamonds(SwitchId switchA, SwitchId switchB, IslStatus status, int cost, int port) {
        // A - B - D
        //   + C +
        Switch nodeA = switchRepository.findById(switchA).get();
        Switch nodeB = switchRepository.findById(switchB).get();
        createIsl(nodeA, nodeB, status, status, cost, 1000, port);
        createIsl(nodeB, nodeA, status, status, cost, 1000, port);
    }

    private void createTriangleTopo(IslStatus pathABstatus, int pathABcost, int pathCcost,
                                    String switchStart, int startIndex) {
        // A - B
        // + C +
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index));

        createIsl(nodeA, nodeB, pathABstatus, pathABstatus, pathABcost, 1000, 5);
        createIsl(nodeB, nodeA, pathABstatus, pathABstatus, pathABcost, 1000, 5);
        createIsl(nodeA, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 6);
        createIsl(nodeC, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 6);
        createIsl(nodeC, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 7);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 7);
    }

    private Switch createSwitch(String name) {
        Switch sw = Switch.builder().switchId(new SwitchId(name)).status(SwitchStatus.ACTIVE).build();

        switchRepository.createOrUpdate(sw);
        return sw;
    }

    private Isl createIsl(Switch srcSwitch, Switch dstSwitch, IslStatus status, IslStatus actual,
                          int cost, long bw, int port) {
        Isl isl = new Isl();
        isl.setSrcSwitch(srcSwitch);
        isl.setDestSwitch(dstSwitch);
        isl.setStatus(status);
        isl.setActualStatus(actual);
        if (cost >= 0) {
            isl.setCost(cost);
        }
        isl.setAvailableBandwidth(bw);
        isl.setLatency(5);
        isl.setSrcPort(port);
        isl.setDestPort(port);

        islRepository.createOrUpdate(isl);
        return isl;
    }

    private FlowPath addPathSegment(FlowPath flowPath, Switch src, Switch dst, int srcPort, int dstPort) {
        PathSegment ps = new PathSegment();
        ps.setPathId(flowPath.getPathId());
        ps.setSrcSwitch(src);
        ps.setDestSwitch(dst);
        ps.setSrcPort(srcPort);
        ps.setDestPort(dstPort);
        ps.setLatency(null);
        ps.setSeqId(flowPath.getSegments().size());
        flowPath.getSegments().add(ps);
        return flowPath;
    }
}
