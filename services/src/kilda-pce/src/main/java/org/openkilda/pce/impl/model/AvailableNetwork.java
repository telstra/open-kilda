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

package org.openkilda.pce.model;

import static org.openkilda.pce.Utils.safeAsInt;

import org.openkilda.messaging.model.SwitchId;

import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Semantically, this class represents an "available network".  That means everything in it is
 * active, and the available bandwidth of the isl links matches the criteria specified.
 * <p/>
 * It supports unidirectional links - ie either Inbound or Outbound can be null.
 */
public class AvailableNetwork {

    private static final Logger logger = LoggerFactory.getLogger(AvailableNetwork.class);

    private HashMap<SwitchId, SimpleSwitch> switches = new HashMap<>();  // key = DPID

    private final Driver driver;

    /**
     * Main constructor that reads topology from the database.
     */
    public AvailableNetwork(Driver driver, boolean ignoreBandwidth, long requestedBandwidth) {
        this.driver = driver;

        buildNetwork(ignoreBandwidth, requestedBandwidth);
    }

    /**
     * Creates empty representation of network topology.
     */
    public AvailableNetwork(Driver driver) {
        this.driver = driver;
    }

    /**
     * This method should be called when the intent is to just initialize the switch.
     * If the switch already exists, it won't be re-initialized.
     *
     * @param dpid the primary key of the switch, ie dpid
     */
    private SimpleSwitch initSwitch(SwitchId dpid) {
        SimpleSwitch result = switches.get(dpid);
        if (result == null) {
            result = new SimpleSwitch(dpid);
            switches.put(dpid, result);
        }
        return result;
    }

    /**
     * Creates switches (if they are not created yet) and ISL between them.
     */
    public AvailableNetwork addLink(SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                                         int cost, int latency) {
        SimpleSwitch srcSwitch = initSwitch(srcDpid);
        SimpleIsl isl = new SimpleIsl(srcDpid, dstDpid, srcPort, dstPort, cost, latency);
        srcSwitch.addOutbound(isl);
        if (cost == 0) {
            logger.warn("Found ZERO COST ISL: {}", isl);
        }
        return this;
    }

    public Map<SwitchId, SimpleSwitch> getSwitches() {
        return switches;
    }

    public SimpleSwitch getSimpleSwitch(SwitchId dpid) {
        return switches.get(dpid);
    }

    /**
     * This call can be used to determine the effect of things like reduceByCost and removeSelfLoops.
     * @return The count of switches, neighbors, ISLs
     */
    public Map<String, Integer> getCounts() {
        Map<String, Integer> result = new HashMap<>();

        result.put("SWITCHES", switches.size());

        int neighbors = 0;
        int islCount = 0;
        for (SimpleSwitch sw : switches.values()) {
            neighbors += sw.outbound.size();
            for (Set<SimpleIsl> isls  : sw.outbound.values()) {
                islCount += isls.size();
            }
        }
        result.put("NEIGHBORS", neighbors);
        result.put("ISLS", islCount);

        return result;
    }

    /**
     * This function can be / should be called after initialization so that all of the checks can be
     * made to ensure the data is okay.
     */
    public void sanityCheck() {
        /*
         * Algorithm:
         * 1) ensure there is only one link from a src to its neighbor (ie dst)
         * 2) ensure there are no self loops (most things should still work, but could lead to bad
         *    behavior, like looping until depth is reached.
         * 3) any negative costs?? remove for now
         */
    }

    /**
     * Call this function to reduce the network to single (directed) links between src and dst
     * switches.  The algorithm runs on the Outgoing and the Incoming links, which could have
     * different values. Consequently, one of these could be null by the end.
     */
    public void reduceByCost() {
        /*
         * Algorithm:
         *  1) Loop through all switches
         *  2) For each switch, loop through its neighbors
         *  3) Per neighbor, reduce the number of links to 1, based on cost.
         */
        for (SimpleSwitch sw : switches.values()) {                 // 1: each switch
            if (sw.outbound.size() < 1) {
                logger.warn("AvailableNetwork: Switch {} has NO OUTBOUND isls", sw.dpid);
                continue;
            }
            for (Entry<SwitchId, Set<SimpleIsl>> linksEntry : sw.outbound.entrySet()) {     // 2: each neighbor
                Set<SimpleIsl> links = linksEntry.getValue();
                if (links.size() <= 1) {
                    continue;  // already at 1 or less
                }

                SimpleIsl cheapestLink = links.stream()
                        .min(Comparator.comparingInt(SimpleIsl::getCost))
                        .get();

                sw.outbound.put(linksEntry.getKey(), Collections.singleton(cheapestLink));
            }
        }
    }

    /**
     * Eliminate any self loops (ie src and dst switch is the same.
     *
     * @return this
     */
    public AvailableNetwork removeSelfLoops() {
        for (SimpleSwitch sw : switches.values()) {
            if (sw.outbound.containsKey(sw.dpid)) {
                sw.outbound.remove(sw.dpid);
            }
        }
        return this;
    }

    /**
     * Since flow might be already existed and occupied some isls we should take it into account when filtering out
     * ISLs that don't have enough available bandwidth.
     * @param flowId current flow id.
     * @param ignoreBandwidth defines whether bandwidth of links should be ignored.
     * @param flowBandwidth required bandwidth amount that should be available on ISLs.
     */
    public void addIslsOccupiedByFlow(String flowId, boolean ignoreBandwidth, long flowBandwidth) {
        String query = ""
                + "MATCH (src:switch)-[fs:flow_segment{flowid: $flow_id}]->(dst:switch) "
                + "MATCH (src)-[link:isl]->(dst) "
                + "WHERE src.state = 'active' AND dst.state = 'active' AND link.status = 'active' "
                + "AND link.src_port = fs.src_port AND link.dst_port = fs.dst_port "
                + "AND ($ignore_bandwidth OR link.available_bandwidth + fs.bandwidth >= $requested_bandwidth) "
                + "RETURN link";

        Value parameters = Values.parameters(
                "flow_id", flowId,
                "ignore_bandwidth", ignoreBandwidth,
                "requested_bandwidth", flowBandwidth);

        List<Relationship> links = loadAvailableLinks(query, parameters);
        addLinksFromRelationships(links);
    }

    /**
     * Reads all active links from the database and creates representation of the network in the form
     * of {@link SimpleSwitch} and {@link SimpleIsl} between them.
     * @param ignoreBandwidth defines whether bandwidth of links should be ignored.
     * @param flowBandwidth required bandwidth amount that should be available on ISLs.
     */
    private void buildNetwork(boolean ignoreBandwidth, long flowBandwidth) {
        String q = "MATCH (src:switch)-[link:isl]->(dst:switch) "
                + " WHERE src.state = 'active' AND dst.state = 'active' AND link.status = 'active' "
                + "   AND src.name IS NOT NULL AND dst.name IS NOT NULL "
                + "   AND ($ignore_bandwidth OR link.available_bandwidth >= $requested_bandwidth) "
                + " RETURN link";

        Value parameters = Values.parameters(
                "ignore_bandwidth", ignoreBandwidth,
                "requested_bandwidth", flowBandwidth
        );

        List<Relationship> links = loadAvailableLinks(q, parameters);
        addLinksFromRelationships(links);
    }

    /**
     * Loads links from database and creates switches with ISLs between them.
     * @param query to be executed.
     * @param parameters list of parameters for the query.
     */
    private List<Relationship> loadAvailableLinks(String query, Value parameters) {
        logger.debug("Executing query for getting links to fill AvailableNetwork: {}", query);
        try (Session session = driver.session(AccessMode.READ)) {
            StatementResult queryResults = session.run(query, parameters);
            return queryResults.list()
                    .stream()
                    .map(record -> record.get("link"))
                    .map(Value::asRelationship)
                    .collect(Collectors.toList());
        }
    }

    private void addLinksFromRelationships(List<Relationship> links) {
        links.forEach(isl ->
                addLink(new SwitchId(isl.get("src_switch").asString()),
                        new SwitchId(isl.get("dst_switch").asString()),
                        safeAsInt(isl.get("src_port")),
                        safeAsInt(isl.get("dst_port")),
                        safeAsInt(isl.get("cost")),
                        safeAsInt(isl.get("latency"))
                ));
    }

    @Override
    public String toString() {
        String result = "AvailableNetwork{";
        StringBuilder sb = new StringBuilder();
        for (SimpleSwitch sw : switches.values()) {
            sb.append(sw);
        }
        result += sb.toString();
        result += "\n}";
        return  result;

    }
}
