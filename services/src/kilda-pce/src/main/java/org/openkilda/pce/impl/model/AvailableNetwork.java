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

package org.openkilda.pce.impl.model;

import org.openkilda.model.SwitchId;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Semantically, this class represents an "available network".  That means everything in it is
 * active, and the available bandwidth of the isl links matches the criteria specified.
 * <p/>
 * It supports unidirectional links - ie either Inbound or Outbound can be null.
 */
@Slf4j
@ToString
public class AvailableNetwork {
    private final Map<SwitchId, SimpleSwitch> switches = new HashMap<>();  // key = DPID

    public Map<SwitchId, SimpleSwitch> getSwitches() {
        return switches;
    }

    public SimpleSwitch getSimpleSwitch(SwitchId dpid) {
        return switches.get(dpid);
    }

    /**
     * Creates switches (if they are not created yet) and ISL between them.
     */
    public void addLink(SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                        int cost, int latency) {
        SimpleSwitch srcSwitch = initSwitch(srcDpid);
        SimpleIsl isl = new SimpleIsl(srcDpid, dstDpid, srcPort, dstPort, cost, latency);
        srcSwitch.addOutbound(isl);
        if (cost == 0) {
            log.warn("Found ZERO COST ISL: {}", isl);
        }
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
     * This call can be used to determine the effect of things like reduceByCost and removeSelfLoops.
     *
     * @return The count of switches, neighbors, ISLs
     */
    public Map<String, Integer> getCounts() {
        Map<String, Integer> result = new HashMap<>();

        result.put("SWITCHES", switches.size());

        int neighbors = 0;
        int islCount = 0;
        for (SimpleSwitch sw : switches.values()) {
            neighbors += sw.outbound.size();
            for (Set<SimpleIsl> isls : sw.outbound.values()) {
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
        throw new UnsupportedOperationException("Not implemented yet.");
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
                log.warn("AvailableNetwork: Switch {} has NO OUTBOUND isls", sw.dpid);
                continue;
            }
            for (Entry<SwitchId, Set<SimpleIsl>> linksEntry : sw.outbound.entrySet()) {     // 2: each neighbor
                Set<SimpleIsl> links = linksEntry.getValue();
                if (links.size() <= 1) {
                    continue;  // already at 1 or less
                }

                links.stream()
                        .min(Comparator.comparingInt(SimpleIsl::getCost))
                        .ifPresent(cheapestLink ->
                                sw.outbound.put(linksEntry.getKey(), Collections.singleton(cheapestLink)));
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
            sw.outbound.remove(sw.dpid);
        }
        return this;
    }
}
