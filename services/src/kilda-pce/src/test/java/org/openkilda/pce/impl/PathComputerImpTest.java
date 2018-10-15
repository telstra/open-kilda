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

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.Node;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputer.Strategy;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.UnroutableFlowException;
import org.openkilda.persistence.Neo4jConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.neo4j.ogm.testutil.TestServer;

import java.util.List;

/**
 * The primary goals of this test package are to emulate the Acceptance Tests in the ATDD module. Those tests can be
 * found in services/src/atdd/src/test/java/org/openkilda/atdd/PathComputationTest.java
 */
public class PathComputerImpTest {

    static TestServer testServer;
    static TransactionManager txManager;
    static SwitchRepository switchRepository;
    static IslRepository islRepository;
    static FlowSegmentRepository flowSegmentRepository;
    static PathComputerImpl pathComputer;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() {
        testServer = new TestServer(true, true, 5);

        PersistenceManager persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(
                new ConfigurationProvider() {
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
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
        pathComputer = new PathComputerImpl(islRepository);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @After
    public void cleanUp() {
        ((Neo4jSessionFactory) txManager).getSession().purgeDatabase();
    }

    private Switch createSwitch(String name) {
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(name));
        sw.setStatus(SwitchStatus.ACTIVE);

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

    /**
     * This will create a diamond with cost as string.
     */
    private void createDiamondAsString(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost,
                                       String switchStart, int startIndex) {
        txManager.begin();
        // A - B - D
        //   + C +
        int index = startIndex;
        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + String.format("%02X", index++));
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

        txManager.commit();
    }

    private void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost) {
        createDiamond(pathBstatus, pathCstatus, pathBcost, pathCcost, "00:", 1);
    }

    private void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost,
                               String switchStart, int startIndex) {
        txManager.begin();
        // A - B - D
        //   + C +
        int index = startIndex;
        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + String.format("%02X", index++));
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


        txManager.commit();
    }

    private void connectDiamonds(SwitchId switchA, SwitchId switchB, IslStatus status, int cost, int port) {
        txManager.begin();
        // A - B - D
        //   + C +
        Switch nodeA = switchRepository.findBySwitchId(switchA);
        Switch nodeB = switchRepository.findBySwitchId(switchB);
        createIsl(nodeA, nodeB, status, status, cost, 1000, port);
        createIsl(nodeB, nodeA, status, status, cost, 1000, port);

        txManager.commit();
    }

    private void createTriangleTopo(IslStatus pathABstatus, int pathABcost, int pathCcost) {
        createTriangleTopo(pathABstatus, pathABcost, pathCcost, "00:", 1);
    }

    private void createTriangleTopo(IslStatus pathABstatus, int pathABcost, int pathCcost,
                                    String switchStart, int startIndex) {
        txManager.begin();
        // A - B
        // + C +
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index++));

        createIsl(nodeA, nodeB, pathABstatus, pathABstatus, pathABcost, 1000, 5);
        createIsl(nodeB, nodeA, pathABstatus, pathABstatus, pathABcost, 1000, 5);
        createIsl(nodeA, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 6);
        createIsl(nodeC, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 6);
        createIsl(nodeC, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 7);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 7);

        txManager.commit();
    }

    @Test
    public void testGetPathByCostActive() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("00:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("00:04"));

        Flow f = new Flow();
        f.setSrcSwitch(srcSwitch);
        f.setDestSwitch(destSwitch);
        f.setBandwidth(100);

        PathPair path = pathComputer.getPath(f, PathComputer.Strategy.COST);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.getForward().getNodes().size());
        Assert.assertEquals(new SwitchId("00:02"), path.getForward().getNodes().get(1).getSwitchId()); // chooses path B
    }


    @Test
    public void testGetPathByCostActive_AsStr() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamondAsString(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "FF:", 1);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("FF:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("FF:04"));

        Flow f = new Flow();
        f.setSrcSwitch(srcSwitch);
        f.setDestSwitch(destSwitch);
        f.setBandwidth(100);

        PathPair path = pathComputer.getPath(f, PathComputer.Strategy.COST);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.getForward().getNodes().size());
        Assert.assertEquals(new SwitchId("FF:02"), path.getForward().getNodes().get(1).getSwitchId()); // chooses path B
    }


    @Test
    public void testGetPathByCostInactive() throws UnroutableFlowException, RecoverableException {
        /*
         * verifies that iSL in both directions needs to be active
         */
        createDiamond(IslStatus.INACTIVE, IslStatus.ACTIVE, 10, 20, "01:", 1);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("01:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("01:04"));

        Flow f = new Flow();
        f.setSrcSwitch(srcSwitch);
        f.setDestSwitch(destSwitch);
        f.setBandwidth(100);

        PathPair path = pathComputer.getPath(f, PathComputer.Strategy.COST);

        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.getForward().getNodes().size());
        // ====> only difference is it should now have C as first hop .. since B is inactive
        Assert.assertEquals(new SwitchId("01:03"), path.getForward().getNodes().get(1).getSwitchId()); // chooses path B
    }

    @Test
    public void testGetPathByCostInactiveOnTriangleTopo() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but lowest path is inactive
         */
        createTriangleTopo(IslStatus.INACTIVE, 5, 20, "02:", 1);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("02:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("02:02"));

        Flow f = new Flow();
        f.setSrcSwitch(srcSwitch);
        f.setDestSwitch(destSwitch);
        f.setBandwidth(100);

        PathPair path = pathComputer.getPath(f, PathComputer.Strategy.COST);
        System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.getForward().getNodes().size());
        // ====> only difference is it should now have C as first hop .. since B is inactive
        Assert.assertEquals(new SwitchId("02:03"), path.getForward().getNodes().get(1).getSwitchId()); // chooses path B
    }

    @Test
    public void testGetPathByCostNoCost() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, -1, 2000, "03:", 1);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("03:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("03:04"));

        Flow f = new Flow();
        f.setSrcSwitch(srcSwitch);
        f.setDestSwitch(destSwitch);
        f.setBandwidth(100);

        PathPair path = pathComputer.getPath(f, PathComputer.Strategy.COST);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.getForward().getNodes().size());
        // ====> Should choose B .. because default cost (700) cheaper than 2000
        Assert.assertEquals(new SwitchId("03:02"), path.getForward().getNodes().get(1).getSwitchId()); // chooses path B
    }


    @Test
    public void testGetPathNoPath() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond(IslStatus.INACTIVE, IslStatus.INACTIVE, 10, 30, "04:", 1);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("04:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("04:04"));

        Flow f = new Flow();
        f.setSrcSwitch(srcSwitch);
        f.setDestSwitch(destSwitch);
        f.setBandwidth(100);

        thrown.expect(UnroutableFlowException.class);

        pathComputer.getPath(f, PathComputer.Strategy.COST);
    }


    /**
     * Test the mechanisms of the in memory getPath.
     */
    @Test
    public void getPathTest_InitState() throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "05:", 1);

        Switch srcSwitch1 = switchRepository.findBySwitchId(new SwitchId("05:01"));
        Switch destSwitch1 = switchRepository.findBySwitchId(new SwitchId("05:03"));

        Flow f1 = new Flow();
        f1.setSrcSwitch(srcSwitch1);
        f1.setDestSwitch(destSwitch1);
        f1.setBandwidth(0);
        f1.setIgnoreBandwidth(false);

        long time = System.currentTimeMillis();

        PathPair path = pathComputer.getPath(f1, Strategy.COST);
        List<Node> result = path.getForward().getNodes();
        System.out.println("TIME: PathComputerImpl.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);

        Switch srcSwitch2 = switchRepository.findBySwitchId(new SwitchId("05:01"));
        Switch destSwitch2 = switchRepository.findBySwitchId(new SwitchId("05:04"));

        Flow f2 = new Flow();
        f2.setSrcSwitch(srcSwitch2);
        f2.setDestSwitch(destSwitch2);
        f2.setBandwidth(0);
        f2.setIgnoreBandwidth(false);

        time = System.currentTimeMillis();

        path = pathComputer.getPath(f2, Strategy.COST);
        result = path.getForward().getNodes();
        System.out.println("TIME: PathComputerImpl.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);
    }

    /**
     * Create a couple of islands .. try to find a path between them .. validate no path is returned, and that the
     * function completes in reasonable time ( < 10ms);
     */
    @Test
    public void getPathTest_Islands() throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "06:", 1);
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "07:", 1);

        Switch srcSwitch1 = switchRepository.findBySwitchId(new SwitchId("06:01"));
        Switch destSwitch1 = switchRepository.findBySwitchId(new SwitchId("06:03"));

        // THIS ONE SHOULD WORK
        Flow f1 = new Flow();
        f1.setSrcSwitch(srcSwitch1);
        f1.setDestSwitch(destSwitch1);
        f1.setBandwidth(0);
        f1.setIgnoreBandwidth(false);

        long time = System.currentTimeMillis();

        PathPair path = pathComputer.getPath(f1, Strategy.COST);
        List<Node> result = path.getForward().getNodes();
        System.out.println("TIME: PathComputerImpl.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);

        Switch srcSwitch2 = switchRepository.findBySwitchId(new SwitchId("06:01"));
        Switch destSwitch2 = switchRepository.findBySwitchId(new SwitchId("07:04"));

        // THIS ONE SHOULD FAIL
        Flow f2 = new Flow();
        f2.setSrcSwitch(srcSwitch2);
        f2.setDestSwitch(destSwitch2);
        f2.setBandwidth(0);
        f2.setIgnoreBandwidth(false);

        time = System.currentTimeMillis();

        thrown.expect(UnroutableFlowException.class);

        path = pathComputer.getPath(f2, Strategy.COST);
        result = path.getForward().getNodes();
        System.out.println("TIME: PathComputerImpl.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);
    }

    /**
     * See how it works with a large network. It takes a while to create the network .. therefore @Ignore so that it
     * doesn't slow down unit tests.
     */
    @Test
    public void getPathTest_Large() throws RecoverableException, UnroutableFlowException {
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

        Switch srcSwitch1 = switchRepository.findBySwitchId(new SwitchId("10:01"));
        Switch destSwitch1 = switchRepository.findBySwitchId(new SwitchId("11:03"));

        // THIS ONE SHOULD WORK
        Flow f1 = new Flow();
        f1.setSrcSwitch(srcSwitch1);
        f1.setDestSwitch(destSwitch1);
        f1.setBandwidth(0);
        f1.setIgnoreBandwidth(false);

        long time = System.currentTimeMillis();

        PathPair path = pathComputer.getPath(f1, Strategy.COST, false, 200);
        List<Node> result = path.getForward().getNodes();
        System.out.println("TIME: PathComputerImpl.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("Path Length = " + result.size());

        Switch srcSwitch2 = switchRepository.findBySwitchId(new SwitchId("08:01"));
        Switch destSwitch2 = switchRepository.findBySwitchId(new SwitchId("11:04"));

        // THIS ONE SHOULD FAIL
        Flow f2 = new Flow();
        f2.setSrcSwitch(srcSwitch2);
        f2.setDestSwitch(destSwitch2);
        f2.setBandwidth(0);
        f2.setIgnoreBandwidth(false);

        time = System.currentTimeMillis();

        thrown.expect(UnroutableFlowException.class);

        path = pathComputer.getPath(f2, Strategy.COST);
        result = path.getForward().getNodes();
        System.out.println("TIME: PathComputerImpl.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("Path Length = " + result.size());
    }

    /**
     * This verifies that the getPath in NeoDriver returns what we expect. Essentially, this tests the additional logic
     * wrt taking the results of the algo and convert to something installable.
     */
    @Test
    public void verifyConversionToPair() throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "09:", 1);

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("09:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("09:04"));

        Flow flow = new Flow();
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(destSwitch);
        flow.setIgnoreBandwidth(false);
        flow.setBandwidth(10);

        PathPair result = pathComputer.getPath(flow, PathComputer.Strategy.COST);
        // ensure start/end switches match
        List<Node> left = result.getForward().getNodes();
        Assert.assertEquals(srcSwitch.getSwitchId(), left.get(0).getSwitchId());
        Assert.assertEquals(destSwitch.getSwitchId(), left.get(left.size() - 1).getSwitchId());
        List<Node> right = result.getReverse().getNodes();
        Assert.assertEquals(destSwitch.getSwitchId(), right.get(0).getSwitchId());
        Assert.assertEquals(srcSwitch.getSwitchId(), right.get(right.size() - 1).getSwitchId());
    }

    /**
     * Checks that existed flow should always have available path even there is only links with 0 available bandwidth.
     */
    @Test
    public void shouldAlwaysFindPathForExistedFlow() throws RecoverableException, UnroutableFlowException {
        Flow flow = new Flow();
        flow.setFlowId("flow-A1:01-A1:03");
        flow.setBandwidth(1000);
        flow.setIgnoreBandwidth(false);

        createLinearTopoWithFlowSegments(10, "A1:", 1, 0L,
                flow.getFlowId(), flow.getBandwidth());

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("A1:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("A1:03"));

        flow.setSrcSwitch(srcSwitch);      // getPath will find an isl port
        flow.setDestSwitch(destSwitch);

        PathPair result = pathComputer.getPath(flow, Strategy.COST, true);

        Assert.assertEquals(4, result.getForward().getNodes().size());
        Assert.assertEquals(4, result.getReverse().getNodes().size());
    }

    /**
     * Tests the case when we try to increase bandwidth of the flow and there is no available bandwidth left.
     */
    @Test(expected = UnroutableFlowException.class)
    public void shouldNotFindPathForExistedFlowAndIncreasedBandwidth()
            throws RecoverableException, UnroutableFlowException {
        long originFlowBandwidth = 1000L;

        Flow flow = new Flow();
        flow.setBandwidth(originFlowBandwidth);
        flow.setIgnoreBandwidth(false);
        flow.setFlowId("flow-A1:01-A1:03");

        // create network, all links have available bandwidth 0
        createLinearTopoWithFlowSegments(10, "A1:", 1, 0,
                flow.getFlowId(), flow.getBandwidth());

        Switch srcSwitch = switchRepository.findBySwitchId(new SwitchId("A1:01"));
        Switch destSwitch = switchRepository.findBySwitchId(new SwitchId("A1:03"));

        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(destSwitch);

        long updatedFlowBandwidth = originFlowBandwidth + 1;
        flow.setBandwidth(updatedFlowBandwidth);
        pathComputer.getPath(flow, Strategy.COST, true);
    }

    private void createLinearTopoWithFlowSegments(int cost, String switchStart, int startIndex, long linkBw,
                                                  String flowId, long flowBandwidth) {
        txManager.begin();

        int index = startIndex;
        // A - B - C
        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index));
        createIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 5);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 6);
        createIsl(nodeC, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 6);
        createIsl(nodeB, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 5);

        addFlowSegment(flowId, flowBandwidth, nodeA, nodeB, 5, 5);
        addFlowSegment(flowId, flowBandwidth, nodeB, nodeA, 5, 5);
        addFlowSegment(flowId, flowBandwidth, nodeB, nodeC, 6, 6);
        addFlowSegment(flowId, flowBandwidth, nodeC, nodeB, 6, 6);

        txManager.commit();
    }

    private void addFlowSegment(String flowId, long flowBandwidth, Switch src, Switch dst, int srcPort, int dstPort) {
        FlowSegment fs = new FlowSegment();
        fs.setFlowId(flowId);
        fs.setSrcSwitch(src);
        fs.setSrcPort(srcPort);
        fs.setDestSwitch(dst);
        fs.setDestPort(dstPort);
        fs.setBandwidth(flowBandwidth);
        flowSegmentRepository.createOrUpdate(fs);
    }
}
