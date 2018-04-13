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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Semantically, this class represents an "available network".  That means everything in it is
 * active, and the available bandwidth of the isl links matches the criteria specified.
 *
 * It supports unidirectional links - ie either Inbound or Outbound can be null.
 */
public class AvailableNetwork {

    private static final Logger logger = LoggerFactory.getLogger(AvailableNetwork.class);

    private HashMap<String, SimpleSwitch> switches = new HashMap();  // key = DPID

    /**
     * This method should be called when the intent is to just initialize the switch.
     * If the switch already exists, it won't be re-initialized.
     *
     * @param dpid the primary key of the switch, ie dpid
     */
    public SimpleSwitch initSwitch(String dpid) {
        SimpleSwitch result = switches.get(dpid);
        if (result == null) {
            result = new SimpleSwitch(dpid);
            switches.put(dpid, result);
        }
        return result;
    }

    public AvailableNetwork initOneEntry(String src_dpid, String dst_dpid, int src_port, int dst_port,
                                         int cost, int latency) {
        SimpleSwitch src_sw;
        SimpleIsl isl;

        src_sw = initSwitch(src_dpid);
        isl = new SimpleIsl(src_dpid, dst_dpid, src_port, dst_port, cost, latency);
        src_sw.addOutbound(isl);
        if (cost == 0)
            logger.warn("Found ZERO COST ISL: {}", isl);
        return this;
    }

    public Map<String, SimpleSwitch> getSwitches() {
        return switches;
    }

    /**
     * @return the SimpleSwitch associated with the dpid
     */
    public SimpleSwitch getSimpleSwitch(String dpid) {
        return switches.get(dpid);
    }


    /**
     * This call can be used to determine the effect of things like reduceByCost and removeSelfLoops
     * @return The count of switches, neighbors, ISLs
     */
    public Map<String, Integer> getCounts() {
        Map<String, Integer> result = new HashMap<>();

        result.put("SWITCHES", switches.size());

        int neighbors = 0;
        int isl_count = 0;
        for (SimpleSwitch sw : switches.values()) {
            neighbors += sw.outbound.size();
            for (Set<SimpleIsl> isls  : sw.outbound.values())
                isl_count += isls.size();
        }
        result.put("NEIGHBORS", neighbors);
        result.put("ISLS", isl_count);

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
     *
     * @return this
     */
    public AvailableNetwork reduceByCost() {
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
            for (Set<SimpleIsl> islSet : sw.outbound.values()){     // 2: each neighbor
                if (islSet.size() <= 1 )
                    continue;  // already at 1 or less
                SimpleIsl[] isls = islSet.toArray(new SimpleIsl[0]);
                SimpleIsl min = isls[0];
                for (int i = 1; i < isls.length; i++) {             // 3: reduce links to 1
                    SimpleIsl current = isls[i];
                    if (current.cost < min.cost){
                        sw.outbound.values().remove(min);
                        min = current;
                    } else {
                        sw.outbound.values().remove(current);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Eliminate any self loops (ie src and dst switch is the same.
     *
     * @return this
     */
    public AvailableNetwork removeSelfLoops() {
        for (SimpleSwitch sw : switches.values()) {
            if (sw.outbound.containsKey(sw.dpid))
                sw.outbound.remove(sw.dpid);
        }
        return this;
    }


    @Override
    public String toString() {
        String result = "AvailableNetwork{";
        for (SimpleSwitch sw : switches.values())
            result += sw ;
        result += "\n}";
        return  result;

    }
}
