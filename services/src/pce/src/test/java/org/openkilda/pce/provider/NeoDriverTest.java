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

package org.openkilda.pce.provider;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.SwitchId;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.configuration.BoltConnector;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class NeoDriverTest {

    private NeoDriver target = new NeoDriver(GraphDatabase.driver("bolt://localhost:7878",
            AuthTokens.basic("neo4j", "neo4j")));
    private static GraphDatabaseService graphDb;

    private static final File databaseDirectory = new File("target/neo4j-test-db");

    @BeforeClass
    public static void setUpOnce() throws Exception {
        FileUtils.deleteRecursively(databaseDirectory);       // delete neo db file

        // This next area enables Kilda to connect to the local db
        BoltConnector bolt = new BoltConnector("0");
        graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(databaseDirectory)
                .setConfig(bolt.type, "BOLT")
                .setConfig(bolt.enabled, "true")
                .setConfig(bolt.listen_address, "localhost:7878")
                .newGraphDatabase();
    }

    @AfterClass
    public static void tearDownOnce() {
        graphDb.shutdown();
    }


    @Test
    public void getAllFlows() {
        try (Transaction tx = graphDb.beginTx()) {
            Node node1 = graphDb.createNode(Label.label("switch"));
            node1.setProperty("name", "00:01");
            Node node2 = graphDb.createNode(Label.label("switch"));
            node2.setProperty("name", "00:02");
            Relationship rel1 = node1.createRelationshipTo(node2, RelationshipType.withName("flow"));
            rel1.setProperty("flowid", "f1");
            rel1.setProperty("cookie", 3);
            rel1.setProperty("meter_id", 2);
            rel1.setProperty("transit_vlan", 1);
            rel1.setProperty("src_switch", "00:01");
            rel1.setProperty("dst_switch", "00:02");
            rel1.setProperty("src_port", 1);
            rel1.setProperty("dst_port", 2);
            rel1.setProperty("src_vlan", 5);
            rel1.setProperty("dst_vlan", 5);
            rel1.setProperty("path", "\"{\"path\": [], \"latency_ns\": 0, \"timestamp\": 1522528031909}\"");
            rel1.setProperty("bandwidth", 200);
            rel1.setProperty("ignore_bandwidth", true);
            rel1.setProperty("description", "description");
            rel1.setProperty("last_updated", 1213333331L);
            rel1.setProperty("status", "IN_PROGRESS");
            tx.success();
        }

        List<Flow> flows = target.getAllFlows();
        Flow flow = flows.get(0);
        Assert.assertEquals(3, flow.getCookie());
        Assert.assertEquals("f1", flow.getFlowId());
        Assert.assertEquals(true, flow.isIgnoreBandwidth());
    }


    @Test
    public void getAllIsl() {
        try (Transaction tx = graphDb.beginTx()) {
            Node node1 = graphDb.createNode(Label.label("switch"));
            node1.setProperty("name", "00:01");
            Node node2 = graphDb.createNode(Label.label("switch"));
            node2.setProperty("name", "00:02");
            Relationship rel1 = node1.createRelationshipTo(node2, RelationshipType.withName("isl"));
            rel1.setProperty("src_switch", "00:01");
            rel1.setProperty("src_port", 1);
            rel1.setProperty("dst_switch", "00:02");
            rel1.setProperty("dst_port", 2);
            rel1.setProperty("speed", 200);
            rel1.setProperty("max_bandwidth", 300);
            rel1.setProperty("latency", 400);
            rel1.setProperty("available_bandwidth", 500);
            rel1.setProperty("status", "active");

            Relationship rel2 = node2.createRelationshipTo(node1, RelationshipType.withName("isl"));
            rel2.setProperty("src_switch", "00:02");
            rel2.setProperty("src_port", 3);
            rel2.setProperty("dst_switch", "00:01");
            rel2.setProperty("dst_port", 4);
            rel2.setProperty("speed", 600);
            rel2.setProperty("max_bandwidth", 700);
            rel2.setProperty("latency", 800);
            rel2.setProperty("available_bandwidth", 900);
            rel2.setProperty("status", "INACTIVE");

            tx.success();
        }

        List<IslInfoData> isls = target.getIsls();
        IslInfoData isl = isls.get(0);
        Assert.assertEquals(200, isl.getSpeed());
        Assert.assertEquals(new SwitchId("00:01"), isl.getPath().get(0).getSwitchId());
        Assert.assertEquals(IslChangeType.DISCOVERED, isl.getState());

        isl = isls.get(1);
        Assert.assertEquals(IslChangeType.FAILED, isl.getState());

    }


    @Test
    public void getAllSwitches() {
        try (Transaction tx = graphDb.beginTx()) {
            Node node1 = graphDb.createNode(Label.label("switch"));
            node1.setProperty("name", "00:00:00:00:00:00:00:01");
            node1.setProperty("address", "1");
            node1.setProperty("hostname", "2");
            node1.setProperty("description", "3");
            node1.setProperty("controller", "4");
            node1.setProperty("state", "active");

            Node node2 = graphDb.createNode(Label.label("switch"));
            node2.setProperty("name", "00:00:00:00:00:00:00:02");
            node2.setProperty("address", "5");
            node2.setProperty("hostname", "6");
            node2.setProperty("description", "7");
            node2.setProperty("controller", "8");
            node2.setProperty("state", "inactive");

            tx.success();
        }

        List<SwitchInfoData> switches = target.getSwitches();
        SwitchInfoData switch1 = switches.get(0);
        Assert.assertEquals(new SwitchId("00:00:00:00:00:00:00:01"), switch1.getSwitchId());
        Assert.assertEquals(SwitchState.ACTIVATED, switch1.getState());

        SwitchInfoData switch2 = switches.get(1);
        Assert.assertEquals(new SwitchId("00:00:00:00:00:00:00:02"), switch2.getSwitchId());
        Assert.assertNotEquals(SwitchState.ACTIVATED, switch2.getState());
    }

    @Test
    public void getSwitchById() {
        try (Transaction tx = graphDb.beginTx()) {
            Node node1 = graphDb.createNode(Label.label("switch"));
            node1.setProperty("name", "00:00:00:00:00:00:00:03");
            node1.setProperty("address", "1");
            node1.setProperty("hostname", "2");
            node1.setProperty("description", "3");
            node1.setProperty("controller", "4");
            node1.setProperty("state", "active");

            tx.success();
        }
        SwitchId expectedSwitchId = new SwitchId("00:00:00:00:00:00:00:03");
        Optional<SwitchInfoData> result = target.getSwitchById(expectedSwitchId);
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(expectedSwitchId, result.get().getSwitchId());
    }

    @Test
    public void getSwitchByIdNotFound() {
        SwitchId expectedSwitchId = new SwitchId("00:00:00:00:00:00:00:01");
        Optional<SwitchInfoData> result = target.getSwitchById(expectedSwitchId);
        Assert.assertFalse(result.isPresent());
    }
}
